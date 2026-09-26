package tw.app.taiwanweather.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException
import tw.app.taiwanweather.data.local.CachedPayload
import tw.app.taiwanweather.data.local.NoOpWeatherCache
import tw.app.taiwanweather.data.local.RoomWeatherCache
import tw.app.taiwanweather.data.local.WeatherCache
import tw.app.taiwanweather.data.local.WeatherCacheDatabase
import tw.app.taiwanweather.location.GeoDistance
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherRepository(
    private val cwa: CwaApi = ApiProvider.cwa,
    private val moenv: MoenvApi = ApiProvider.moenv,
    private val cache: WeatherCache = NoOpWeatherCache,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val flightScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flightMutex = Mutex()
    private val flights = mutableMapOf<RequestKey, Deferred<WeatherReport>>()

    suspend fun townships(county: String, cwaKey: String): List<String> {
        require(cwaKey.isNotBlank()) { "請先設定中央氣象署授權碼" }
        val dataset = TaiwanCounties.firstOrNull { it.name == county }?.datasetId ?: error("不支援此縣市")
        val root = cwa.forecast(dataset, cwaKey)
        ensureCwaSuccess(root)
        return forecastLocations(root).mapNotNull { it.text("LocationName", "locationName") }.distinct()
    }

    suspend fun report(place: Place, cwaKey: String, moenvKey: String): WeatherReport =
        report(place, ApiKeys(cwaKey, moenvKey))

    suspend fun report(
        place: Place,
        keys: ApiKeys,
        target: GeoPoint? = null,
        forceRefresh: Boolean = false
    ): WeatherReport {
        require(keys.cwa.isNotBlank()) { "請先到設定輸入中央氣象署授權碼" }
        val request = RequestKey(place, keys.cwa, keys.moenv, target, forceRefresh)
        val deferred = flightMutex.withLock {
            flights[request] ?: flightScope.async { reportInternal(place, keys, target, forceRefresh) }
                .also { flights[request] = it }
        }
        return try {
            deferred.await()
        } finally {
            flightMutex.withLock { if (flights[request] === deferred && deferred.isCompleted) flights.remove(request) }
        }
    }

    private suspend fun reportInternal(
        place: Place,
        keys: ApiKeys,
        target: GeoPoint?,
        forceRefresh: Boolean
    ): WeatherReport = supervisorScope {
        val county = TaiwanCounties.firstOrNull { it.name == place.county } ?: error("不支援此地點天氣")
        val forecast = async { source(WeatherSource.FORECAST, county.datasetId + ":" + place.township, forceRefresh) {
            cwa.forecast(county.datasetId, keys.cwa, locationName = place.township).also(::ensureCwaSuccess)
        } }
        val observations = async { source(WeatherSource.OBSERVATIONS, "taiwan", forceRefresh) {
            cwa.observations(keys.cwa).also(::ensureCwaSuccess)
        } }
        val alerts = async { source(WeatherSource.ALERTS, "taiwan", forceRefresh) {
            cwa.alerts(keys.cwa).also(::ensureCwaSuccess)
        } }
        val air = async {
            if (keys.moenv.isBlank()) SourceResult(WeatherSource.AIR, null)
            else source(WeatherSource.AIR, "taiwan", forceRefresh) { parseMoenvResponse(moenv.airQuality(keys.moenv)) }
        }
        val results = listOf(forecast.await(), observations.await(), air.await(), alerts.await())
        val forecastResult = results[0]
        val location = forecastResult.root?.let(::forecastLocations)?.firstOrNull {
            it.text("LocationName", "locationName") == place.township
        }
        val parsed = location?.let(::parseForecast) ?: ParsedForecast(CurrentWeather(), emptyList(), emptyList())
        val observed = results[1].root?.let { findObservation(it, place, target) }
        val current = observed?.let {
            parsed.current.copy(
                temperature = it.temperature.ifBlank { parsed.current.temperature },
                humidity = it.humidity.ifBlank { parsed.current.humidity },
                description = it.description.ifBlank { parsed.current.description },
                wind = it.wind.ifBlank { parsed.current.wind },
                dewPoint = it.dewPoint.ifBlank { parsed.current.dewPoint },
                pressure = it.pressure.ifBlank { parsed.current.pressure },
                precipitation = it.precipitation.ifBlank { parsed.current.precipitation },
                rainfall1Hour = it.rainfall1Hour.ifBlank { parsed.current.rainfall1Hour },
                rainfall3Hours = it.rainfall3Hours.ifBlank { parsed.current.rainfall3Hours },
                rainfall24Hours = it.rainfall24Hours.ifBlank { parsed.current.rainfall24Hours },
                gustSpeed = it.gustSpeed.ifBlank { parsed.current.gustSpeed },
                beaufortScale = it.beaufortScale.ifBlank { parsed.current.beaufortScale },
                sunshineDuration = it.sunshineDuration.ifBlank { parsed.current.sunshineDuration },
                station = it.station
            )
        } ?: parsed.current
        val fetched = results.mapNotNull { it.fetchedAt }.maxOrNull() ?: clock.millis()
        WeatherReport(
            place = place,
            current = current,
            forecast = parsed.daily,
            airQuality = results[2].root?.let { findAirQuality(it, place, target) },
            updatedAt = Instant.ofEpochMilli(fetched).atZone(clock.zone).format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
            hourly = parsed.hourly,
            alerts = results[3].root?.let { parseAlerts(it, place) }.orEmpty(),
            issues = results.mapNotNull { it.issue },
            cache = CacheState(
                fromCache = results.any { it.fromCache },
                stale = results.any { it.stale },
                sources = results.filter { it.fromCache }.map { it.source }.toSet()
            ),
            updatedEpochMillis = fetched
        )
    }

    private suspend fun source(
        source: WeatherSource,
        key: String,
        forceRefresh: Boolean,
        fetch: suspend () -> JsonObject
    ): SourceResult {
        val kind = source.name.lowercase()
        val cached = cache.get(kind, key)?.takeIf { it.schemaVersion == CACHE_SCHEMA }
        val fresh = cached != null && clock.millis() - cached.fetchedAt <= ttl(source)
        if (fresh && !forceRefresh) return SourceResult(source, cached!!.json(), true, false, cached.fetchedAt)
        return try {
            val root = fetch()
            val now = clock.millis()
            cache.put(CachedPayload(kind, key, root.toString(), now, CACHE_SCHEMA))
            SourceResult(source, root, fetchedAt = now)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (cached != null) SourceResult(source, cached.json(), true, true, cached.fetchedAt, issue(source, error))
            else SourceResult(source, null, issue = issue(source, error))
        }
    }

    private fun ttl(source: WeatherSource) = when (source) {
        WeatherSource.FORECAST -> 60 * 60_000L
        WeatherSource.OBSERVATIONS -> 15 * 60_000L
        WeatherSource.AIR -> 30 * 60_000L
        WeatherSource.ALERTS -> 10 * 60_000L
    }

    suspend fun testCwa(key: String): String {
        require(key.isNotBlank()) { "請輸入中央氣象署授權碼" }
        ensureCwaSuccess(cwa.forecast("F-D0047-063", key, locationName = "中正區"))
        return "中央氣象署連線成功"
    }

    suspend fun testMoenv(key: String): String {
        require(key.isNotBlank()) { "請輸入環境部 API Key" }
        require(parseMoenvResponse(moenv.airQuality(key, limit = 1)).array("records").isNotEmpty()) {
            "環境部授權碼無效或查無資料"
        }
        return "環境部連線成功"
    }

    internal fun parseMoenvBody(body: String): JsonObject {
        val content = body.trim()
        require(content.isNotBlank()) { "環境部沒有回傳資料" }
        val element = runCatching { JSON.parseToJsonElement(content) }.getOrElse {
            throw IllegalArgumentException(content.take(200))
        }
        return when (element) {
            is JsonObject -> element
            is JsonArray -> buildJsonObject { put("records", element) }
            else -> throw IllegalArgumentException("環境部回傳格式錯誤")
        }
    }

    private fun parseMoenvResponse(response: retrofit2.Response<okhttp3.ResponseBody>): JsonObject {
        val content = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        if (!response.isSuccessful) throw IllegalArgumentException(
            content?.trim()?.takeIf(String::isNotBlank) ?: "環境部資料服務回應錯誤 (${response.code()})"
        )
        return parseMoenvBody(content.orEmpty())
    }

    internal fun parseForecast(location: JsonObject): ParsedForecast {
        val elements = location.array("WeatherElement", "weatherElement").map { it.jsonObject }
        fun times(vararg names: String): List<JsonObject> = elements.firstOrNull {
            it.text("ElementName", "elementName") in names
        }?.array("Time", "time").orEmpty().map { it.jsonObject }
        fun at(series: List<JsonObject>, key: String): JsonObject? {
            series.firstOrNull { it.timeKey() == key }?.let { return it }
            val target = parseInstant(key) ?: return null
            return series.firstOrNull { item ->
                val start = item.text("StartTime", "startTime", "DataTime", "dataTime")?.let(::parseInstant)
                val end = item.text("EndTime", "endTime")?.let(::parseInstant)
                start != null && end != null && !target.isBefore(start) && target.isBefore(end)
            }
        }
        fun value(time: JsonObject?, vararg names: String): String? {
            val item = time?.array("ElementValue", "elementValue")?.firstOrNull() as? JsonObject ?: return null
            return (names.firstNotNullOfOrNull { item.text(it) } ?: item.values.firstNotNullOfOrNull { it.stringValue() })
                ?.takeUnless { it.isMissingValue() }
        }
        val weather = times("天氣現象", "天氣預報綜合描述")
        val temp = times("平均溫度")
        val min = times("最低溫度")
        val max = times("最高溫度")
        val humidity = times("平均相對濕度")
        val minApparent = times("最低體感溫度")
        val maxApparent = times("最高體感溫度")
        val rain = times("12小時降雨機率", "降雨機率")
        val windSpeed = times("風速")
        val windDirection = times("風向")
        val uv = times("紫外線指數", "UVI")
        val dewPoint = times("平均露點溫度")
        val comfort = times("舒適度指數", "舒適度")
        val beaufort = times("蒲福風級")
        require(weather.isNotEmpty()) { "氣象署沒有提供此地點資料" }
        fun wind(key: String) = listOfNotNull(
            value(at(windDirection, key), "WindDirection", "value"),
            value(at(windSpeed, key), "WindSpeed", "value")?.let { "$it m/s" }
        ).joinToString(" ").ifBlank { "--" }
        val weatherByKey = weather.mapNotNull { item -> item.timeKey()?.let { it to item } }.toMap()
        val keys = weatherByKey.keys.sorted()
        val now = clock.instant()
        val futureKeys = keys.filter { key -> parseInstant(key)?.let { !it.isBefore(now) } ?: true }
        val first = futureKeys.firstOrNull() ?: keys.first()
        val current = CurrentWeather(
            temperature = value(at(temp, first), "Temperature", "value") ?: "--",
            apparentTemperature = value(at(maxApparent, first), "MaxApparentTemperature", "value") ?: "--",
            description = value(weatherByKey[first], "Weather", "WeatherDescription", "value") ?: "--",
            humidity = value(at(humidity, first), "RelativeHumidity", "value") ?: "--",
            rainProbability = value(at(rain, first), "ProbabilityOfPrecipitation", "value") ?: "--",
            wind = wind(first),
            dewPoint = value(at(dewPoint, first), "DewPoint", "DewPointTemperature", "value") ?: "--",
            beaufortScale = value(at(beaufort, first), "BeaufortScale", "value") ?: "--",
            uvIndex = value(at(uv, first), "UVIndex", "UVI", "value") ?: "--",
            comfort = value(at(comfort, first), "ComfortIndexDescription", "Comfort", "ComfortIndex", "value") ?: "--"
        )
        val end = now.plusSeconds(48 * 60 * 60)
        val hourlyKeys = futureKeys.filter { key ->
            val instant = parseInstant(key)
            instant == null || !instant.isAfter(end)
        }
        val hourly = hourlyKeys.map { key ->
            HourlyForecast(
                startTime = weatherByKey[key]?.text("StartTime", "startTime") ?: key,
                dataTime = weatherByKey[key]?.text("DataTime", "dataTime"),
                temperature = value(at(temp, key), "Temperature", "value") ?: "--",
                description = value(weatherByKey[key], "Weather", "WeatherDescription", "value") ?: "--",
                rainProbability = value(at(rain, key), "ProbabilityOfPrecipitation", "value") ?: "--",
                humidity = value(at(humidity, key), "RelativeHumidity", "value") ?: "--",
                apparentTemperature = value(at(maxApparent, key), "MaxApparentTemperature", "value") ?: "--",
                wind = wind(key),
                uvIndex = value(at(uv, key), "UVIndex", "UVI", "value") ?: "--",
                dewPoint = value(at(dewPoint, key), "DewPoint", "DewPointTemperature", "value") ?: "--",
                comfort = value(at(comfort, key), "ComfortIndex", "ComfortIndexDescription", "Comfort", "value") ?: "--",
                minTemperature = value(at(min, key), "MinTemperature", "value") ?: "--",
                maxTemperature = value(at(max, key), "MaxTemperature", "value") ?: "--",
                minApparentTemperature = value(at(minApparent, key), "MinApparentTemperature", "value") ?: "--",
                maxApparentTemperature = value(at(maxApparent, key), "MaxApparentTemperature", "value") ?: "--",
                beaufortScale = value(at(beaufort, key), "BeaufortScale", "value") ?: "--"
            )
        }
        val daily = keys.groupBy { it.take(10) }.entries.take(7).map { (date, dayKeys) ->
            DailyForecast(
                date,
                value(weatherByKey[dayKeys.first()], "Weather", "WeatherDescription", "value") ?: "--",
                dayKeys.mapNotNull { value(at(min, it), "MinTemperature", "value")?.toIntOrNull() }.minOrNull()?.toString() ?: "--",
                dayKeys.mapNotNull { value(at(max, it), "MaxTemperature", "value")?.toIntOrNull() }.maxOrNull()?.toString() ?: "--",
                dayKeys.mapNotNull { value(at(rain, it), "ProbabilityOfPrecipitation", "value")?.toIntOrNull() }.maxOrNull()?.toString() ?: "--",
                uvIndex = dayKeys.mapNotNull { value(at(uv, it), "UVIndex", "UVI", "value")?.toDoubleOrNull() }.maxOrNull()?.formatNumber() ?: "--",
                minApparentTemperature = dayKeys.mapNotNull { value(at(minApparent, it), "MinApparentTemperature", "value")?.toIntOrNull() }.minOrNull()?.toString() ?: "--",
                maxApparentTemperature = dayKeys.mapNotNull { value(at(maxApparent, it), "MaxApparentTemperature", "value")?.toIntOrNull() }.maxOrNull()?.toString() ?: "--",
                comfort = dayKeys.firstNotNullOfOrNull { value(at(comfort, it), "ComfortIndexDescription", "Comfort", "ComfortIndex", "value") } ?: "--"
            )
        }
        return ParsedForecast(current, daily, hourly)
    }

    internal fun findObservation(root: JsonObject, place: Place, target: GeoPoint?): CurrentWeather? {
        val stations = root.obj("records")?.array("Station", "station", "location").orEmpty().map { it.jsonObject }
        val candidates = stations.mapNotNull { station ->
            val rawWeather = station["WeatherElement"] ?: station["weatherElement"] ?: return@mapNotNull null
            val weather = when (rawWeather) {
                is JsonObject -> rawWeather
                is JsonArray -> JsonObject(rawWeather.filterIsInstance<JsonObject>().flatMap { it.entries }.associate { it.toPair() })
                else -> return@mapNotNull null
            }
            val temperature = weather.deepText("AirTemperature", "airTemperature").validNumber() ?: return@mapNotNull null
            val geo = station.obj("GeoInfo", "geoInfo")
            val coordinate = station.coordinate() ?: geo?.coordinate()
            Observation(station, weather, geo, coordinate, temperature)
        }
        val selected = if (target != null) candidates.filter { it.coordinate != null }.minByOrNull {
            GeoDistance.kilometers(target, it.coordinate!!)
        } else candidates.firstOrNull {
            normalize(it.geo?.text("CountyName", "countyName")) == normalize(place.county) &&
                normalize(it.geo?.text("TownName", "townName")) == normalize(place.township)
        } ?: candidates.firstOrNull { normalize(it.geo?.text("CountyName", "countyName")) == normalize(place.county) }
        selected ?: return null
        fun valid(vararg keys: String) = selected.weather.deepText(*keys).validNumber().orEmpty()
        fun nestedValue(container: String, vararg keys: String) = selected.weather.descendantObjects()
            .firstNotNullOfOrNull { obj -> obj.entries.firstOrNull { it.key.equals(container, true) }?.value as? JsonObject }
            ?.deepText(*keys).validNumber().orEmpty()
        val windSpeed = valid("WindSpeed", "windSpeed")
        val gust = nestedValue("GustInfo", "PeakGustSpeed", "peakGustSpeed", "WindSpeed", "windSpeed")
            .ifBlank { valid("PeakGustSpeed", "peakGustSpeed", "MaxGustSpeed", "maxGustSpeed") }
        val apiBeaufort = selected.weather.deepText("BeaufortScale", "beaufortScale").validNumber()
        fun rain(vararg keys: String) = selected.weather.deepText(*keys).validNumber().orEmpty()
        val sunshineMinutes = selected.weather.deepText("SunshineDurationMinutes", "sunshineDurationMinutes")
            .validNumber()?.toDoubleOrNull()
        val sunshine = sunshineMinutes?.div(60.0)?.formatNumber()
            ?: selected.weather.deepText("SunshineDuration", "sunshineDuration", "SunshineHours", "sunshineHours")
                .validNumber().orEmpty()
        val distance = target?.let { selected.coordinate?.let { point -> GeoDistance.kilometers(it, point) } }
        val stationInfo = StationInfo(
            selected.station.text("StationName", "stationName", "locationName").orEmpty(),
            selected.station.deepText("ObsTime", "obsTime", "DateTime", "dateTime")
                ?.let(::observationTimeText).orEmpty(),
            selected.coordinate,
            distance
        )
        return CurrentWeather(
            temperature = selected.temperature,
            description = selected.weather.deepText("Weather", "weather").orEmpty().takeUnless { it.isMissingValue() }.orEmpty(),
            humidity = valid("RelativeHumidity", "relativeHumidity"),
            wind = windSpeed.let { if (it.isBlank()) "" else "$it m/s" },
            dewPoint = valid("DewPoint", "dewPoint", "DewPointTemperature", "dewPointTemperature"),
            pressure = valid("AirPressure", "airPressure", "StationPressure", "stationPressure"),
            precipitation = nestedValue("Now", "Precipitation", "precipitation").ifBlank {
                valid("Precipitation", "precipitation", "Rainfall", "rainfall")
            },
            rainfall1Hour = rain("Past1hr", "past1hr", "Past1Hour", "past1Hour", "Rainfall1Hour", "rainfall1Hour"),
            rainfall3Hours = rain("Past3hr", "past3hr", "Past3Hours", "past3Hours", "Rainfall3Hours", "rainfall3Hours"),
            rainfall24Hours = rain("Past24hr", "past24hr", "Past24Hours", "past24Hours", "Rainfall24Hours", "rainfall24Hours"),
            gustSpeed = gust,
            beaufortScale = apiBeaufort ?: windSpeed.toDoubleOrNull()?.let(::windSpeedToBeaufort)?.toString().orEmpty(),
            sunshineDuration = sunshine,
            station = stationInfo
        )
    }

    internal fun findAirQuality(root: JsonObject, place: Place, target: GeoPoint?): AirQuality? {
        val records = root.array("records").map { it.jsonObject }.filter { it.text("aqi")?.toDoubleOrNull() != null }
        val selected = if (target != null) records.mapNotNull { record -> record.coordinate()?.let { record to it } }
            .minByOrNull { GeoDistance.kilometers(target, it.second) }?.first
        else records.firstOrNull { normalize(it.text("county")) == normalize(place.county) && normalize(it.text("township")) == normalize(place.township) }
            ?: records.firstOrNull { normalize(it.text("county")) == normalize(place.county) }
        selected ?: return null
        val point = selected.coordinate()
        val publishTime = selected.text("publishtime")?.let(::observationTimeText).orEmpty()
        return AirQuality(
            selected.text("aqi").orEmpty().ifBlank { "--" },
            selected.text("status").orEmpty().ifBlank { "無資料" },
            selected.text("pm2.5", "pm2_5").orEmpty().ifBlank { "--" },
            selected.text("sitename").orEmpty(),
            publishTime,
            StationInfo(selected.text("sitename").orEmpty(), publishTime, point,
                target?.let { point?.let { p -> GeoDistance.kilometers(it, p) } })
            , pm10 = selected.cleanText("pm10"),
            o3 = selected.cleanText("o3"),
            co = selected.cleanText("co"),
            so2 = selected.cleanText("so2"),
            no2 = selected.cleanText("no2"),
            pollutant = selected.cleanText("pollutant"),
            o3_8hr = selected.cleanText("o3_8hr", "o3_8h"),
            co_8hr = selected.cleanText("co_8hr", "co_8h"),
            pm10Average = selected.cleanText("pm10_avg", "pm10_average"),
            pm25Average = selected.cleanText("pm2.5_avg", "pm2_5_avg", "pm25_avg")
        )
    }

    internal fun parseAlerts(root: JsonObject, place: Place): List<WeatherAlert> {
        val now = clock.instant()
        return root.descendantObjects().mapNotNull { item ->
            val title = item.text("headline", "title", "phenomena", "event") ?: return@mapNotNull null
            val areas = item.deepStrings("areaDesc", "areaName", "locationName", "geocode", "affectedAreas")
            if (areas.none { normalize(it).contains(normalize(place.county)) || normalize(it).contains(normalize(place.township)) }) return@mapNotNull null
            val expires = item.deepText("expires", "endTime", "effectiveEndTime", "expireTime").orEmpty()
            parseInstant(expires)?.let { if (it.isBefore(now)) return@mapNotNull null }
            WeatherAlert(
                id = item.deepText("identifier", "id", "capId") ?: "$title:${expires}",
                title = title,
                description = item.deepText("description", "descriptionText", "instruction", "content").orEmpty(),
                issuedAt = item.deepText("sent", "issueTime", "effective", "startTime").orEmpty(),
                expiresAt = expires,
                affectedAreas = areas.distinct()
            )
        }.distinctBy { it.id }.toList()
    }

    private fun ensureCwaSuccess(root: JsonObject) {
        require(root.text("success") != "false") { root.obj("result")?.text("message") ?: "中央氣象署授權碼無效" }
    }

    private fun forecastLocations(root: JsonObject): List<JsonObject> {
        val records = root.obj("records") ?: return emptyList()
        return records.array("Locations", "locations").firstOrNull()?.jsonObject
            ?.array("Location", "location").orEmpty().map { it.jsonObject }
    }

    private fun issue(source: WeatherSource, error: Throwable) = SourceIssue(source, friendlyError(error))
    private fun normalize(value: String?) = value.orEmpty().replace("台", "臺")

    companion object {
        private const val CACHE_SCHEMA = 1
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun create(context: Context, clock: Clock = Clock.systemDefaultZone()): WeatherRepository {
            val database = Room.databaseBuilder(
                context.applicationContext,
                WeatherCacheDatabase::class.java,
                "weather-cache.db"
            ).build()
            return WeatherRepository(cache = RoomWeatherCache(database.cacheDao()), clock = clock)
        }

        fun friendlyError(error: Throwable): String = when (error) {
            is HttpException -> when (error.code()) {
                401, 403 -> "授權碼無效或已過期"
                429 -> "API 使用次數已達上限，請稍後再試"
                else -> "資料服務回應錯誤 (${error.code()})"
            }
            is java.net.UnknownHostException -> "目前無法連上網路"
            is java.net.SocketTimeoutException -> "連線逾時，請稍後再試"
            else -> error.message ?: "讀取資料失敗"
        }
    }

    internal data class ParsedForecast(
        val current: CurrentWeather,
        val daily: List<DailyForecast>,
        val hourly: List<HourlyForecast>
    )

    private data class RequestKey(val place: Place, val cwa: String, val moenv: String, val target: GeoPoint?, val force: Boolean)
    private data class SourceResult(
        val source: WeatherSource,
        val root: JsonObject?,
        val fromCache: Boolean = false,
        val stale: Boolean = false,
        val fetchedAt: Long? = null,
        val issue: SourceIssue? = null
    )
    private data class Observation(
        val station: JsonObject,
        val weather: JsonObject,
        val geo: JsonObject?,
        val coordinate: GeoPoint?,
        val temperature: String
    )
}

private fun CachedPayload.json() = Json.parseToJsonElement(payload).jsonObject
private fun String?.validNumber(): String? = this?.toDoubleOrNull()?.takeIf { it > -90 }?.let {
    it.formatNumber()
}
private fun Double.formatNumber() = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun String.isMissingValue() = isBlank() || trim() in setOf("-99", "-99.0", "-999", "--", "NA", "N/A", "null")
private fun JsonObject.cleanText(vararg keys: String) = text(*keys)?.takeUnless { it.isMissingValue() } ?: "--"

internal fun windSpeedToBeaufort(metersPerSecond: Double): Int = when {
    metersPerSecond < 0 -> 0
    metersPerSecond < 0.3 -> 0
    metersPerSecond < 1.6 -> 1
    metersPerSecond < 3.4 -> 2
    metersPerSecond < 5.5 -> 3
    metersPerSecond < 8.0 -> 4
    metersPerSecond < 10.8 -> 5
    metersPerSecond < 13.9 -> 6
    metersPerSecond < 17.2 -> 7
    metersPerSecond < 20.8 -> 8
    metersPerSecond < 24.5 -> 9
    metersPerSecond < 28.5 -> 10
    metersPerSecond < 32.7 -> 11
    else -> 12
}
private fun JsonObject.timeKey() = text("StartTime", "startTime", "DataTime", "dataTime")
private fun JsonObject.coordinate(): GeoPoint? {
    fun JsonObject.direct(): GeoPoint? {
        val lat = text("StationLatitude", "stationLatitude", "latitude", "Latitude", "lat")?.toDoubleOrNull()
        val lon = text("StationLongitude", "stationLongitude", "longitude", "Longitude", "lon")?.toDoubleOrNull()
        return if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null
    }
    direct()?.let { return it }
    return descendantObjects().firstOrNull {
        it.text("CoordinateName", "coordinateName")?.contains("WGS84", ignoreCase = true) == true
    }?.direct() ?: descendantObjects().firstNotNullOfOrNull { it.direct() }
}
private fun JsonObject.descendantObjects(): Sequence<JsonObject> = sequence {
    yield(this@descendantObjects)
    values.forEach { value ->
        when (value) {
            is JsonObject -> yieldAll(value.descendantObjects())
            is JsonArray -> value.forEach { if (it is JsonObject) yieldAll(it.descendantObjects()) }
            else -> Unit
        }
    }
}
private fun JsonObject.deepText(vararg keys: String): String? = descendantObjects().firstNotNullOfOrNull { it.text(*keys) }
private fun JsonObject.deepStrings(vararg keys: String): List<String> = descendantObjects().flatMap { obj ->
    keys.asSequence().mapNotNull { obj[it] }.flatMap { value ->
        when (value) {
            is JsonArray -> value.asSequence().mapNotNull { it.stringValue() ?: (it as? JsonObject)?.deepText("value", "areaDesc", "areaName") }
            else -> sequenceOf(value.stringValue()).filterNotNull()
        }
    }
}.toList()
private fun parseInstant(value: String): Instant? {
    if (value.isBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: LOCAL_DATE_TIME_FORMATS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter).atZone(OBSERVATION_ZONE).toInstant() }.getOrNull()
        }
}
private fun observationTimeText(value: String): String = parseInstant(value)
    ?.atZone(OBSERVATION_ZONE)
    ?.format(OBSERVATION_TIME_FORMAT)
    ?: value
private val OBSERVATION_ZONE = ZoneId.of("Asia/Taipei")
private val OBSERVATION_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
private val LOCAL_DATE_TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
)
private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { get(it)?.stringValue() }
private fun JsonElement.stringValue(): String? = runCatching { jsonPrimitive.content }.getOrNull()
private fun JsonObject.obj(vararg keys: String): JsonObject? = keys.firstNotNullOfOrNull { get(it) as? JsonObject }
private fun JsonObject.array(vararg keys: String): JsonArray = keys.firstNotNullOfOrNull { get(it) as? JsonArray } ?: JsonArray(emptyList())

package tw.app.taiwanweather.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WeatherRepository(
    private val cwa: CwaApi = ApiProvider.cwa,
    private val moenv: MoenvApi = ApiProvider.moenv
) {
    suspend fun townships(county: String, cwaKey: String): List<String> {
        require(cwaKey.isNotBlank()) { "請先設定中央氣象署授權碼" }
        val dataset = TaiwanCounties.firstOrNull { it.name == county }?.datasetId
            ?: error("不支援此縣市")
        val root = cwa.forecast(dataset, cwaKey)
        ensureCwaSuccess(root)
        return forecastLocations(root).mapNotNull { it.text("LocationName", "locationName") }.distinct()
    }

    suspend fun report(place: Place, cwaKey: String, moenvKey: String): WeatherReport {
        require(cwaKey.isNotBlank()) { "請先到設定輸入中央氣象署授權碼" }
        val county = TaiwanCounties.firstOrNull { it.name == place.county } ?: error("不支援此地點天氣")
        val forecastRoot = cwa.forecast(county.datasetId, cwaKey, locationName = place.township)
        ensureCwaSuccess(forecastRoot)
        val location = forecastLocations(forecastRoot).firstOrNull {
            it.text("LocationName", "locationName") == place.township
        } ?: error("找不到 ${place.title} 的預報資料")

        val parsed = parseForecast(location)
        val observation = runCatching { findObservation(cwa.observations(cwaKey), place) }.getOrNull()
        val current = observation?.let {
            parsed.first.copy(
                temperature = it.temperature.takeUnless(String::isBlank) ?: parsed.first.temperature,
                humidity = it.humidity.takeUnless(String::isBlank) ?: parsed.first.humidity,
                description = it.description.takeUnless(String::isBlank) ?: parsed.first.description,
                wind = it.wind.takeUnless(String::isBlank) ?: parsed.first.wind
            )
        } ?: parsed.first
        val air = if (moenvKey.isBlank()) null else runCatching {
            findAirQuality(parseMoenvResponse(moenv.airQuality(moenvKey)), place)
        }.getOrNull()
        return WeatherReport(
            place = place,
            current = current,
            forecast = parsed.second,
            airQuality = air,
            updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
        )
    }

    suspend fun testCwa(key: String): String {
        require(key.isNotBlank()) { "請輸入中央氣象署授權碼" }
        ensureCwaSuccess(cwa.forecast("F-D0047-063", key, locationName = "中正區"))
        return "中央氣象署連線成功"
    }

    suspend fun testMoenv(key: String): String {
        require(key.isNotBlank()) { "請輸入環境部 API Key" }
        val records = parseMoenvResponse(moenv.airQuality(key, limit = 1)).array("records")
        require(records.isNotEmpty()) { "環境部授權碼無效或查無資料" }
        return "環境部連線成功"
    }

    internal fun parseMoenvBody(body: String): JsonObject {
        val content = body.trim()
        require(content.isNotBlank()) { "環境部沒有回傳資料" }
        val element = runCatching { Json.parseToJsonElement(content) }.getOrElse {
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
        if (!response.isSuccessful) {
            val detail = content?.trim()?.takeIf { it.isNotBlank() }
            throw IllegalArgumentException(detail ?: "環境部資料服務回應錯誤 (${response.code()})")
        }
        return parseMoenvBody(content.orEmpty())
    }

    private fun parseForecast(location: JsonObject): Pair<CurrentWeather, List<DailyForecast>> {
        val elements = location.array("WeatherElement", "weatherElement")
        val byName = elements.map { it.jsonObject }.associateBy { it.text("ElementName", "elementName").orEmpty() }
        fun entries(name: String) = byName[name]?.array("Time", "time").orEmpty().map { it.jsonObject }
        fun value(time: JsonObject?, vararg names: String): String? {
            val item = time?.array("ElementValue", "elementValue")?.firstOrNull()?.jsonObject ?: return null
            return names.firstNotNullOfOrNull { item.text(it) } ?: item.values.firstOrNull()?.stringValue()
        }

        val weather = entries("天氣現象")
        val avgTemp = entries("平均溫度")
        val minTemp = entries("最低溫度")
        val maxTemp = entries("最高溫度")
        val humidity = entries("平均相對濕度")
        val apparent = entries("最高體感溫度")
        val rain = entries("12小時降雨機率")
        val windSpeed = entries("風速")
        val windDirection = entries("風向")
        require(weather.isNotEmpty()) { "氣象署沒有提供此地點資料" }

        val current = CurrentWeather(
            temperature = value(avgTemp.firstOrNull(), "Temperature", "value") ?: "--",
            apparentTemperature = value(apparent.firstOrNull(), "MaxApparentTemperature", "value") ?: "--",
            description = value(weather.firstOrNull(), "Weather", "value") ?: "--",
            humidity = value(humidity.firstOrNull(), "RelativeHumidity", "value") ?: "--",
            rainProbability = value(rain.firstOrNull(), "ProbabilityOfPrecipitation", "value") ?: "--",
            wind = listOfNotNull(
                value(windDirection.firstOrNull(), "WindDirection", "value"),
                value(windSpeed.firstOrNull(), "WindSpeed", "value")?.let { "$it m/s" }
            ).joinToString(" ").ifBlank { "--" }
        )

        val days = linkedMapOf<String, MutableList<Int>>()
        weather.forEachIndexed { index, time ->
            val date = time.text("StartTime", "startTime")?.take(10) ?: return@forEachIndexed
            days.getOrPut(date) { mutableListOf() }.add(index)
        }
        val forecast = days.entries.take(7).map { (date, indexes) ->
            val mins = indexes.mapNotNull { value(minTemp.getOrNull(it), "MinTemperature", "value")?.toIntOrNull() }
            val maxs = indexes.mapNotNull { value(maxTemp.getOrNull(it), "MaxTemperature", "value")?.toIntOrNull() }
            val pops = indexes.mapNotNull { value(rain.getOrNull(it), "ProbabilityOfPrecipitation", "value")?.toIntOrNull() }
            DailyForecast(
                date = date,
                description = value(weather.getOrNull(indexes.first()), "Weather", "value") ?: "--",
                minTemperature = mins.minOrNull()?.toString() ?: "--",
                maxTemperature = maxs.maxOrNull()?.toString() ?: "--",
                rainProbability = pops.maxOrNull()?.toString() ?: "--"
            )
        }
        return current to forecast
    }

    private fun findObservation(root: JsonObject, place: Place): CurrentWeather? {
        ensureCwaSuccess(root)
        val stations = root.obj("records")?.array("Station", "station", "location").orEmpty().map { it.jsonObject }
        val station = stations.firstOrNull {
            val geo = it.obj("GeoInfo", "geoInfo")
            normalize(geo?.text("CountyName", "countyName")) == normalize(place.county) &&
                normalize(geo?.text("TownName", "townName")) == normalize(place.township)
        } ?: stations.firstOrNull {
            normalize(it.obj("GeoInfo", "geoInfo")?.text("CountyName", "countyName")) == normalize(place.county)
        } ?: return null
        val weather = station.obj("WeatherElement", "weatherElement") ?: return null
        fun valid(vararg keys: String) = weather.text(*keys)?.toDoubleOrNull()?.takeIf { it > -90 }?.let { n ->
            if (n % 1.0 == 0.0) n.toInt().toString() else "%.1f".format(n)
        }.orEmpty()
        return CurrentWeather(
            temperature = valid("AirTemperature", "airTemperature"),
            description = weather.text("Weather", "weather").orEmpty().takeUnless { it == "-99" }.orEmpty(),
            humidity = valid("RelativeHumidity", "relativeHumidity"),
            wind = valid("WindSpeed", "windSpeed").let { if (it.isBlank()) "" else "$it m/s" }
        )
    }

    private fun findAirQuality(root: JsonObject, place: Place): AirQuality? {
        val records = root.array("records").map { it.jsonObject }
        val record = records.firstOrNull { normalize(it.text("county")) == normalize(place.county) }
            ?: return null
        return AirQuality(
            aqi = record.text("aqi").orEmpty().ifBlank { "--" },
            status = record.text("status").orEmpty().ifBlank { "無資料" },
            pm25 = record.text("pm2.5", "pm2_5").orEmpty().ifBlank { "--" },
            siteName = record.text("sitename").orEmpty(),
            publishTime = record.text("publishtime").orEmpty()
        )
    }

    private fun ensureCwaSuccess(root: JsonObject) {
        val success = root.text("success")
        require(success == "true") { root.obj("result")?.text("message") ?: "中央氣象署授權碼無效" }
    }

    private fun forecastLocations(root: JsonObject): List<JsonObject> {
        val records = root.obj("records") ?: return emptyList()
        val groups = records.array("Locations", "locations")
        return groups.firstOrNull()?.jsonObject?.array("Location", "location").orEmpty().map { it.jsonObject }
    }

    private fun normalize(value: String?) = value.orEmpty().replace("台", "臺")

    companion object {
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
}

private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    get(key)?.stringValue()
}

private fun JsonElement.stringValue(): String? = runCatching { jsonPrimitive.content }.getOrNull()
private fun JsonObject.obj(vararg keys: String): JsonObject? = keys.firstNotNullOfOrNull { get(it) as? JsonObject }
private fun JsonObject.array(vararg keys: String): JsonArray = keys.firstNotNullOfOrNull { get(it) as? JsonArray } ?: JsonArray(emptyList())

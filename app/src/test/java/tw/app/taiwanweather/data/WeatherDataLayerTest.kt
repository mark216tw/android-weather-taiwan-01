package tw.app.taiwanweather.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import tw.app.taiwanweather.data.local.InMemoryWeatherCache
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WeatherDataLayerTest {
    private val json = Json
    private val clock = Clock.fixed(Instant.parse("2026-09-23T04:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `hourly fields join by time rather than array index`() {
        val repository = WeatherRepository(clock = clock)
        val location = json.parseToJsonElement("""{
          "WeatherElement":[
            {"ElementName":"天氣現象","Time":[
              {"StartTime":"2026-09-23T12:00:00+08:00","ElementValue":[{"Weather":"晴"}]},
              {"StartTime":"2026-09-23T15:00:00+08:00","ElementValue":[{"Weather":"雨"}]}]},
            {"ElementName":"平均溫度","Time":[
              {"StartTime":"2026-09-23T15:00:00+08:00","ElementValue":[{"Temperature":"25"}]},
              {"StartTime":"2026-09-23T12:00:00+08:00","ElementValue":[{"Temperature":"30"}]}]}
          ]
        }""").jsonObject

        val hourly = repository.parseForecast(location).hourly

        assertEquals(listOf("30", "25"), hourly.map { it.temperature })
    }

    @Test
    fun `forecast interval values cover official forecast time with exact match preferred`() {
        val location = json.parseToJsonElement("""{
          "WeatherElement":[
            {"ElementName":"天氣現象","Time":[
              {"StartTime":"2026-09-23T12:00:00+08:00","ElementValue":[{"Weather":"晴"}]},
              {"StartTime":"2026-09-23T15:00:00+08:00","ElementValue":[{"Weather":"多雲"}]}]},
            {"ElementName":"紫外線指數","Time":[
              {"StartTime":"2026-09-23T06:00:00+08:00","EndTime":"2026-09-23T18:00:00+08:00","ElementValue":[{"UVIndex":"7"}]},
              {"StartTime":"2026-09-23T15:00:00+08:00","ElementValue":[{"UVIndex":"4"}]}]},
            {"ElementName":"舒適度","Time":[
              {"StartTime":"2026-09-23T12:00:00+08:00","EndTime":"2026-09-23T18:00:00+08:00","ElementValue":[{"ComfortIndexDescription":"舒適"}]}]}
          ]
        }""").jsonObject

        val hourly = WeatherRepository(clock = clock).parseForecast(location).hourly

        assertEquals(listOf("7", "4"), hourly.map { it.uvIndex })
        assertEquals(listOf("舒適", "舒適"), hourly.map { it.comfort })
    }

    @Test
    fun `observation parses nested rain gust and derives beaufort while hiding sentinels`() {
        val root = json.parseToJsonElement("""{"records":{"Station":[{
          "StationName":"測站","GeoInfo":{"CountyName":"臺北市","TownName":"中正區"},
          "WeatherElement":{"AirTemperature":"28","DewPoint":"-99","AirPressure":"1008.2",
            "WindSpeed":"12.0","Now":{"Precipitation":"3.5"},"GustInfo":{"PeakGustSpeed":"18.2"}}
        }]}}""").jsonObject

        val current = WeatherRepository(clock = clock).findObservation(root, Place("臺北市", "中正區"), null)!!

        assertEquals("3.5", current.precipitation)
        assertEquals("18.2", current.gustSpeed)
        assertEquals("6", current.beaufortScale)
        assertEquals("", current.dewPoint)
    }

    @Test
    fun `air quality parses complete lowercase MOENV fields and aliases`() {
        val root = json.parseToJsonElement("""{"records":[{"county":"臺北市","township":"中正區",
          "aqi":"42","status":"良好","pm2.5":"11","pm10":"22","o3":"31","co":"0.3","so2":"2","no2":"9",
          "pollutant":"臭氧","o3_8hr":"28","co_8hr":"0.2","pm10_avg":"20","pm2.5_avg":"10","sitename":"站","publishtime":"now"}]}""").jsonObject

        val air = WeatherRepository(clock = clock).findAirQuality(root, Place("臺北市", "中正區"), null)!!

        assertEquals(listOf("22", "31", "0.3", "2", "9"), listOf(air.pm10, air.o3, air.co, air.so2, air.no2))
        assertEquals("臭氧", air.pollutant)
        assertEquals(listOf("28", "0.2", "20", "10"), listOf(air.o3_8hr, air.co_8hr, air.pm10Average, air.pm25Average))
    }

    @Test
    fun `beaufort conversion follows standard boundaries`() {
        assertEquals(0, windSpeedToBeaufort(0.2))
        assertEquals(1, windSpeedToBeaufort(0.3))
        assertEquals(6, windSpeedToBeaufort(12.0))
        assertEquals(12, windSpeedToBeaufort(32.7))
    }

    @Test
    fun `alerts filter unrelated and expired records defensively`() {
        val repository = WeatherRepository(clock = clock)
        val root = json.parseToJsonElement("""{"records":{"alerts":[
          {"identifier":"active","headline":"豪雨特報","area":{"areaDesc":"臺北市、中正區"},"expires":"2026-09-23T08:00:00Z"},
          {"identifier":"other","headline":"大雨特報","area":{"areaDesc":"高雄市"},"expires":"2026-09-23T08:00:00Z"},
          {"identifier":"old","headline":"低溫特報","area":{"areaDesc":"臺北市"},"expires":"2026-09-22T08:00:00Z"}
        ]}}""").jsonObject

        val alerts = repository.parseAlerts(root, Place("臺北市", "中正區"))

        assertEquals(listOf("active"), alerts.map { it.id })
    }

    @Test
    fun `fresh cache suppresses duplicate source requests`() = runTest {
        val cwa = FakeCwa()
        val repository = WeatherRepository(cwa, FakeMoenv, InMemoryWeatherCache(), clock)

        val first = repository.report(Place("臺北市", "中正區"), ApiKeys("key"))
        val second = repository.report(Place("臺北市", "中正區"), ApiKeys("key"))

        assertFalse(first.cache.fromCache)
        assertTrue(second.cache.fromCache)
        assertEquals(1, cwa.forecasts)
        assertEquals(1, cwa.observations)
        assertEquals(1, cwa.alerts)
    }

    private class FakeCwa : CwaApi {
        var forecasts = 0
        var observations = 0
        var alerts = 0

        override suspend fun forecast(dataset: String, key: String, format: String, locationName: String?): JsonObject {
            forecasts++
            return Json.parseToJsonElement("""{"success":"true","records":{"Locations":[{"Location":[{
              "LocationName":"中正區","WeatherElement":[{"ElementName":"天氣現象","Time":[
                {"StartTime":"2026-09-23T12:00:00+08:00","ElementValue":[{"Weather":"晴"}]}]}]
            }]}]}}""").jsonObject
        }

        override suspend fun observations(key: String, format: String): JsonObject {
            observations++
            return Json.parseToJsonElement("""{"success":"true","records":{"Station":[]}}""").jsonObject
        }

        override suspend fun alerts(key: String, format: String): JsonObject {
            alerts++
            return Json.parseToJsonElement("""{"success":"true","records":{}}""").jsonObject
        }
    }

    private object FakeMoenv : MoenvApi {
        override suspend fun airQuality(key: String, offset: Int, limit: Int, format: String): Response<ResponseBody> =
            error("MOENV should not be called without a key")
    }
}

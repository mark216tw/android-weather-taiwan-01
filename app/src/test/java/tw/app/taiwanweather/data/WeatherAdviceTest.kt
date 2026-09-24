package tw.app.taiwanweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherAdviceTest {
    @Test
    fun `AQI uses official six level boundaries`() {
        assertEquals(AqiLevel.GOOD, aqiHealthAdvice("50")?.level)
        assertEquals(AqiLevel.MODERATE, aqiHealthAdvice("51")?.level)
        assertEquals(AqiLevel.SENSITIVE, aqiHealthAdvice("101")?.level)
        assertEquals(AqiLevel.UNHEALTHY, aqiHealthAdvice("151")?.level)
        assertEquals(AqiLevel.VERY_UNHEALTHY, aqiHealthAdvice("201")?.level)
        assertEquals(AqiLevel.HAZARDOUS, aqiHealthAdvice("301")?.level)
        assertNull(aqiHealthAdvice("--"))
    }

    @Test
    fun `UV advice uses all official exposure boundaries`() {
        assertEquals(UvLevel.LOW, uvProtectionAdvice("2")?.level)
        assertEquals(UvLevel.MODERATE, uvProtectionAdvice("3")?.level)
        assertEquals(UvLevel.HIGH, uvProtectionAdvice("6")?.level)
        assertEquals(UvLevel.VERY_HIGH, uvProtectionAdvice("8")?.level)
        assertEquals(UvLevel.EXTREME, uvProtectionAdvice("11")?.level)
        assertNull(uvProtectionAdvice("--"))
        assertNull(uvProtectionAdvice("-1"))
    }

    @Test
    fun `summary prioritizes high afternoon rain`() {
        val report = report(
            HourlyForecast("2026-09-23T09:00:00+08:00", temperature = "32", rainProbability = "10"),
            HourlyForecast("2026-09-23T15:00:00+08:00", temperature = "31", rainProbability = "70")
        )

        assertEquals("午後降雨機率高，外出記得帶傘。", todayWeatherSummary(report))
    }

    @Test
    fun `summary provides heat and wind advice`() {
        assertEquals(
            "今日高溫炎熱，記得補充水分並做好防曬。",
            todayWeatherSummary(report(HourlyForecast("2026-09-23T09:00:00+08:00", temperature = "36", rainProbability = "0")))
        )
        assertEquals(
            "今日風勢較強，外出請留意強風與掉落物。",
            todayWeatherSummary(report(HourlyForecast("2026-09-23T09:00:00+08:00", temperature = "25", rainProbability = "0", wind = "12 m/s")))
        )
    }

    private fun report(vararg hourly: HourlyForecast) = WeatherReport(
        place = Place("臺北市", "中正區"),
        current = CurrentWeather(temperature = "25", description = "晴", rainProbability = "0"),
        forecast = emptyList(),
        airQuality = null,
        updatedAt = "09/23 09:00",
        hourly = hourly.toList()
    )
}

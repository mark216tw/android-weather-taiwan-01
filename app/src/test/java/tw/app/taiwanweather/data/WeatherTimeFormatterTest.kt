package tw.app.taiwanweather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTimeFormatterTest {
    @Test
    fun `formats offset time without displaying timezone suffix`() {
        assertEquals("09/23 12:00", formatHourlyTime("2026-09-23T12:00:00+08:00"))
        assertEquals("09/23 15:00", formatHourlyTime("2026-09-23T15:00:00+08:00"))
    }

    @Test
    fun `converts UTC time to Taiwan time`() {
        assertEquals("09/24 00:00", formatHourlyTime("2026-09-23T16:00:00Z"))
    }

    @Test
    fun `supports CWA local date time format`() {
        assertEquals("09/24 03:00", formatHourlyTime("2026-09-24 03:00:00"))
    }
}

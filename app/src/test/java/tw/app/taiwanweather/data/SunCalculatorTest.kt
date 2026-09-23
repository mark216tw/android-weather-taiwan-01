package tw.app.taiwanweather.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunCalculatorTest {
    private val taipei = GeoPoint(25.0330, 121.5654)

    @Test
    fun `Taipei summer sun times are on requested local date`() {
        val times = requireNotNull(SunCalculator.calculate(LocalDate.of(2026, 6, 21), taipei))

        assertEquals(LocalDate.of(2026, 6, 21), times.sunrise.atZone(TAIPEI_ZONE).toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 21), times.sunset.atZone(TAIPEI_ZONE).toLocalDate())
        assertTrue(times.sunriseText() in "04:30".."06:00")
        assertTrue(times.sunsetText() in "18:00".."19:30")
    }

    @Test
    fun `theme is light between sunrise and sunset`() {
        val times = requireNotNull(SunCalculator.calculate(LocalDate.of(2026, 9, 23), taipei))

        assertTrue(SunCalculator.isDark(times.sunrise.minusSeconds(1), times))
        assertFalse(SunCalculator.isDark(times.sunrise, times))
        assertFalse(SunCalculator.isDark(times.sunset.minusSeconds(1), times))
        assertTrue(SunCalculator.isDark(times.sunset, times))
    }
}

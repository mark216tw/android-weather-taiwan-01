package tw.app.taiwanweather.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherSymbolTest {
    @Test
    fun `clear weather uses sun by day and moon by night`() {
        assertEquals("☀", weatherSymbol("晴", isNight = false))
        assertEquals("🌙", weatherSymbol("晴", isNight = true))
        assertEquals("🌤", weatherSymbol("晴時多雲", isNight = false))
        assertEquals("🌙", weatherSymbol("晴時多雲", isNight = true))
    }

    @Test
    fun `precipitation symbols take priority at night`() {
        assertEquals("⛈", weatherSymbol("晴午後雷陣雨", isNight = true))
        assertEquals("🌧", weatherSymbol("晴時有雨", isNight = true))
        assertEquals("🌨", weatherSymbol("晴時有雪", isNight = true))
    }
}

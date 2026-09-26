package tw.app.taiwanweather.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.app.taiwanweather.data.AqiLevel

class AqiColorTest {
    @Test
    fun `dark theme uses muted AQI colors`() {
        assertEquals(Color(0xFF245C3D), AqiLevel.GOOD.aqiColor(dark = true))
        assertEquals(Color(0xFF665D20), AqiLevel.MODERATE.aqiColor(dark = true))
        assertEquals(Color(0xFF714A1E), AqiLevel.SENSITIVE.aqiColor(dark = true))
        assertEquals(Color(0xFF702D2D), AqiLevel.UNHEALTHY.aqiColor(dark = true))
        assertEquals(Color(0xFF593259), AqiLevel.VERY_UNHEALTHY.aqiColor(dark = true))
        assertEquals(Color(0xFF4D2830), AqiLevel.HAZARDOUS.aqiColor(dark = true))
    }

    @Test
    fun `light theme keeps standard AQI colors`() {
        assertEquals(Color(0xFF00E800), AqiLevel.GOOD.aqiColor(dark = false))
        assertEquals(Color(0xFFFFFF00), AqiLevel.MODERATE.aqiColor(dark = false))
        assertEquals(Color(0xFFFF7E00), AqiLevel.SENSITIVE.aqiColor(dark = false))
        assertEquals(Color(0xFFFF0000), AqiLevel.UNHEALTHY.aqiColor(dark = false))
        assertEquals(Color(0xFF8F3F97), AqiLevel.VERY_UNHEALTHY.aqiColor(dark = false))
        assertEquals(Color(0xFF7E0023), AqiLevel.HAZARDOUS.aqiColor(dark = false))
    }
}

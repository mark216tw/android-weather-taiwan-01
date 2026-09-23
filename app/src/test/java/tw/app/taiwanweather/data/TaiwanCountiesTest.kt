package tw.app.taiwanweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaiwanCountiesTest {
    @Test
    fun `contains all 22 Taiwan counties without duplicates`() {
        assertEquals(22, TaiwanCounties.size)
        assertEquals(22, TaiwanCounties.map { it.name }.distinct().size)
        assertTrue(TaiwanCounties.all { it.datasetId.matches(Regex("F-D0047-\\d{3}")) })
        assertTrue(TaiwanCounties.all { it.defaultTownship.isNotBlank() })
    }

    @Test
    fun `place title contains county and township`() {
        assertEquals("臺北市 中正區", Place("臺北市", "中正區").title)
    }
}

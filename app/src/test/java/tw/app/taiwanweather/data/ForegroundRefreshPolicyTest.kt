package tw.app.taiwanweather.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshPolicyTest {
    private val now = 10_000_000L

    @Test
    fun `refreshes only after thirty minutes`() {
        assertFalse(ForegroundRefreshPolicy.shouldRefresh(now - 29 * 60_000L - 59_000L, null, now, false))
        assertFalse(ForegroundRefreshPolicy.shouldRefresh(now - 30 * 60_000L, null, now, false))
        assertTrue(ForegroundRefreshPolicy.shouldRefresh(now - 30 * 60_000L - 1L, null, now, false))
    }

    @Test
    fun `missing data refreshes unless request is already active`() {
        assertTrue(ForegroundRefreshPolicy.shouldRefresh(null, null, now, false))
        assertFalse(ForegroundRefreshPolicy.shouldRefresh(null, null, now, true))
    }

    @Test
    fun `failed attempt observes five minute cooldown`() {
        assertFalse(ForegroundRefreshPolicy.shouldRefresh(null, now - 4 * 60_000L, now, false))
        assertTrue(ForegroundRefreshPolicy.shouldRefresh(null, now - 5 * 60_000L, now, false))
    }
}

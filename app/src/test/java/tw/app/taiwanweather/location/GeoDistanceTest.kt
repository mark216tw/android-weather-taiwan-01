package tw.app.taiwanweather.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.app.taiwanweather.data.GeoPoint

class GeoDistanceTest {
    @Test
    fun `same point has zero distance`() {
        assertEquals(0.0, GeoDistance.kilometers(GeoPoint(25.0, 121.5), GeoPoint(25.0, 121.5)), 0.0001)
    }

    @Test
    fun `taipei to kaohsiung is approximately 300 km`() {
        val distance = GeoDistance.kilometers(GeoPoint(25.033, 121.565), GeoPoint(22.627, 120.301))
        assertTrue(distance in 290.0..310.0)
    }
}

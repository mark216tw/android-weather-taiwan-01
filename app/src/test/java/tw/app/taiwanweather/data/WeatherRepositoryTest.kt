package tw.app.taiwanweather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRepositoryTest {
    private val repository = WeatherRepository()

    @Test
    fun `parses MOENV object response`() {
        val root = repository.parseMoenvBody("""{"records":[{"aqi":"42"}]}""")

        assertEquals("42", root["records"]?.toString()?.substringAfter("\"aqi\":\"")?.substringBefore('"'))
    }

    @Test
    fun `wraps MOENV array response as records`() {
        val root = repository.parseMoenvBody("""[{"aqi":"18"}]""")

        assertTrue(root["records"].toString().contains("\"18\""))
    }

    @Test
    fun `preserves MOENV plain text error`() {
        val error = runCatching { repository.parseMoenvBody("api_key 不存在。") }.exceptionOrNull()

        assertEquals("api_key 不存在。", error?.message)
    }
}

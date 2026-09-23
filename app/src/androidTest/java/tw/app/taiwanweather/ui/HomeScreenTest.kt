package tw.app.taiwanweather.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import tw.app.taiwanweather.AppUiState
import tw.app.taiwanweather.data.CacheState
import tw.app.taiwanweather.data.CurrentWeather
import tw.app.taiwanweather.data.DisplayMode
import tw.app.taiwanweather.data.HourlyForecast
import tw.app.taiwanweather.data.LoadState
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.WeatherAlert
import tw.app.taiwanweather.data.WeatherReport

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedReportShowsFreshnessHourlyAndAlerts() {
        val report = WeatherReport(
            place = Place("臺北市", "中正區"),
            current = CurrentWeather(temperature = "28", description = "晴"),
            forecast = emptyList(),
            airQuality = null,
            updatedAt = "09/23 12:00",
            hourly = listOf(HourlyForecast("2026-09-23T15:00:00+08:00", temperature = "27", description = "晴")),
            alerts = listOf(WeatherAlert("alert", "豪雨特報")),
            cache = CacheState(fromCache = true, stale = true)
        )
        compose.setContent {
            TaiwanWeatherTheme(DisplayMode.LIGHT) {
                HomeScreen(
                    state = AppUiState(loadState = LoadState.Success(report)),
                    refresh = {},
                    locate = {},
                    chooseLocation = {},
                    openSettings = {}
                )
            }
        }

        compose.onNodeWithText("目前顯示已儲存資料，內容可能已過期").assertIsDisplayed()
        compose.onNodeWithText("豪雨特報").assertIsDisplayed()
        compose.onNodeWithText("未來 48 小時分時預報").assertIsDisplayed()
    }
}

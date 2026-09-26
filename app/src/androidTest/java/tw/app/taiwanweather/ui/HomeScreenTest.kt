package tw.app.taiwanweather.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import tw.app.taiwanweather.AppUiState
import tw.app.taiwanweather.data.CacheState
import tw.app.taiwanweather.data.AirQuality
import tw.app.taiwanweather.data.CurrentWeather
import tw.app.taiwanweather.data.DailyForecast
import tw.app.taiwanweather.data.HourlyForecast
import tw.app.taiwanweather.data.LoadState
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.SunTimes
import tw.app.taiwanweather.data.WeatherAlert
import tw.app.taiwanweather.data.WeatherReport
import java.time.Instant

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cachedReportShowsFreshnessHourlyAndAlerts() {
        val report = WeatherReport(
            place = Place("臺北市", "中正區"),
            current = CurrentWeather(
                temperature = "28", description = "晴", dewPoint = "22", pressure = "1008",
                rainProbability = "70", precipitation = "3.5", rainfall1Hour = "42", rainfall3Hours = "110",
                rainfall24Hours = "205", beaufortScale = "6", sunshineDuration = "4.6", uvIndex = "6",
                comfort = "舒適"
            ),
            forecast = emptyList(),
            airQuality = AirQuality(
                aqi = "75", status = "普通", pm25 = "18", siteName = "中山", publishTime = "12:00",
                pm10 = "35", o3 = "28", pollutant = "細懸浮微粒"
            ),
            updatedAt = "09/23 12:00",
            hourly = listOf(
                HourlyForecast("2026-09-23T12:00:00+08:00", temperature = "28", description = "晴", rainProbability = "10", humidity = "70", uvIndex = "6"),
                HourlyForecast("2026-09-23T15:00:00+08:00", temperature = "27", description = "晴", rainProbability = "20", humidity = "75", uvIndex = "4"),
                HourlyForecast("2026-09-23T18:00:00+08:00", temperature = "25", description = "多雲", rainProbability = "30", humidity = "80", uvIndex = "1")
            ),
            alerts = listOf(WeatherAlert("alert", "豪雨特報")),
            cache = CacheState(fromCache = true, stale = true)
        )
        compose.setContent {
            TaiwanWeatherTheme(dark = false) {
                HomeScreen(
                    state = AppUiState(
                        loadState = LoadState.Success(report),
                        sunTimes = SunTimes(
                            Instant.parse("2026-09-23T21:45:00Z"),
                            Instant.parse("2026-09-24T09:50:00Z")
                        )
                    ),
                    refresh = {},
                    locate = {},
                    chooseLocation = {},
                    openSettings = {}
                )
            }
        }

        compose.onNodeWithText("目前顯示已儲存資料，內容可能已過期").assertIsDisplayed()
        compose.onNodeWithText("今天也要帶著好心情出門喔 ♡").assertIsDisplayed()
        compose.onNodeWithText("豪雨特報").assertIsDisplayed()
        compose.onNodeWithContentDescription("分享完整天氣頁面").assertIsDisplayed()
        compose.onAllNodesWithText("未來 48 小時分時預報").assertCountEquals(0)
        compose.onNodeWithText("今日白晝").assertIsDisplayed()
        compose.onNodeWithText("今日生活氣象").assertIsDisplayed()
        compose.onNodeWithText("紫外線 6 · 高量級").assertIsDisplayed()
        compose.onNodeWithText("晴　舒適").assertIsDisplayed()
        compose.onNodeWithText("6 級 · 強風").assertIsDisplayed()
        compose.onNodeWithText("今日累積雨量").assertIsDisplayed()
        compose.onAllNodesWithText("紫外線").assertCountEquals(0)
        compose.onNodeWithText("查看詳細氣象").performClick()
        compose.onNodeWithText("日出").assertIsDisplayed()
        compose.onNodeWithText("日落").assertIsDisplayed()
        compose.onNodeWithText("今日日照時數").assertIsDisplayed()
        compose.onAllNodesWithText("未來 24 小時趨勢").assertCountEquals(0)
        compose.onNodeWithText("查看污染物詳細資料").performClick()
        compose.onNodeWithText("PM10").assertIsDisplayed()
    }

    @Test
    fun sharePageContainsExpandedDetailsAndForecast() {
        val report = WeatherReport(
            place = Place("臺北市", "中正區"),
            current = CurrentWeather(
                temperature = "28", description = "晴", dewPoint = "22", pressure = "1008",
                rainfall1Hour = "1", rainfall3Hours = "2", rainfall24Hours = "3",
                sunshineDuration = "4.6"
            ),
            forecast = listOf(
                DailyForecast("2026-09-23", "晴", "24", "30", "20", uvIndex = "6")
            ),
            airQuality = AirQuality(
                aqi = "75", status = "普通", pm25 = "18", siteName = "中山", publishTime = "09/23 12:00",
                pm10 = "35"
            ),
            updatedAt = "09/23 12:00"
        )
        compose.setContent {
            TaiwanWeatherTheme(dark = false) {
                ShareHomePage(
                    report,
                    SunTimes(Instant.parse("2026-09-22T21:45:00Z"), Instant.parse("2026-09-23T09:50:00Z")),
                    isNight = false
                )
            }
        }

        compose.onAllNodesWithText("日出").assertCountEquals(1)
        compose.onAllNodesWithText("1 小時累積雨量").assertCountEquals(1)
        compose.onAllNodesWithText("PM10").assertCountEquals(1)
        compose.onAllNodesWithText("未來幾天").assertCountEquals(1)
        compose.onAllNodesWithText("查看詳細氣象").assertCountEquals(0)
        compose.onAllNodesWithText("查看污染物詳細資料").assertCountEquals(0)
    }
}

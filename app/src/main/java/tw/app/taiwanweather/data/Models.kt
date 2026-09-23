package tw.app.taiwanweather.data

data class TaiwanCounty(val name: String, val datasetId: String, val defaultTownship: String)

enum class DisplayMode { SYSTEM, LIGHT, DARK }

val TaiwanCounties = listOf(
    TaiwanCounty("宜蘭縣", "F-D0047-003", "宜蘭市"), TaiwanCounty("桃園市", "F-D0047-007", "桃園區"),
    TaiwanCounty("新竹縣", "F-D0047-011", "竹北市"), TaiwanCounty("苗栗縣", "F-D0047-015", "苗栗市"),
    TaiwanCounty("彰化縣", "F-D0047-019", "彰化市"), TaiwanCounty("南投縣", "F-D0047-023", "南投市"),
    TaiwanCounty("雲林縣", "F-D0047-027", "斗六市"), TaiwanCounty("嘉義縣", "F-D0047-031", "太保市"),
    TaiwanCounty("屏東縣", "F-D0047-035", "屏東市"), TaiwanCounty("臺東縣", "F-D0047-039", "臺東市"),
    TaiwanCounty("花蓮縣", "F-D0047-043", "花蓮市"), TaiwanCounty("澎湖縣", "F-D0047-047", "馬公市"),
    TaiwanCounty("基隆市", "F-D0047-051", "仁愛區"), TaiwanCounty("新竹市", "F-D0047-055", "東區"),
    TaiwanCounty("嘉義市", "F-D0047-059", "東區"), TaiwanCounty("臺北市", "F-D0047-063", "中正區"),
    TaiwanCounty("高雄市", "F-D0047-067", "苓雅區"), TaiwanCounty("新北市", "F-D0047-071", "板橋區"),
    TaiwanCounty("臺中市", "F-D0047-075", "中區"), TaiwanCounty("臺南市", "F-D0047-079", "中西區"),
    TaiwanCounty("連江縣", "F-D0047-083", "南竿鄉"), TaiwanCounty("金門縣", "F-D0047-087", "金城鎮")
)

data class Place(val county: String, val township: String) {
    val title get() = "$county $township"
}

data class CurrentWeather(
    val temperature: String = "--",
    val apparentTemperature: String = "--",
    val description: String = "尚無資料",
    val humidity: String = "--",
    val rainProbability: String = "--",
    val wind: String = "--"
)

data class DailyForecast(
    val date: String,
    val description: String,
    val minTemperature: String,
    val maxTemperature: String,
    val rainProbability: String
)

data class AirQuality(
    val aqi: String,
    val status: String,
    val pm25: String,
    val siteName: String,
    val publishTime: String
)

data class WeatherReport(
    val place: Place,
    val current: CurrentWeather,
    val forecast: List<DailyForecast>,
    val airQuality: AirQuality?,
    val updatedAt: String
)

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Success(val report: WeatherReport) : LoadState
    data class Error(val message: String) : LoadState
}

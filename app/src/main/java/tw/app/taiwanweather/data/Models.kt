package tw.app.taiwanweather.data

data class TaiwanCounty(val name: String, val datasetId: String, val defaultTownship: String)

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

data class GeoPoint(val latitude: Double, val longitude: Double)

data class StationInfo(
    val name: String,
    val observedAt: String = "",
    val location: GeoPoint? = null,
    val distanceKm: Double? = null
)

data class CurrentWeather(
    val temperature: String = "--",
    val apparentTemperature: String = "--",
    val description: String = "尚無資料",
    val humidity: String = "--",
    val rainProbability: String = "--",
    val wind: String = "--",
    val station: StationInfo? = null
)

data class HourlyForecast(
    val startTime: String,
    val dataTime: String? = null,
    val temperature: String = "--",
    val description: String = "--",
    val rainProbability: String = "--",
    val humidity: String = "--",
    val apparentTemperature: String = "--",
    val wind: String = "--"
)

data class WeatherAlert(
    val id: String,
    val title: String,
    val description: String = "",
    val issuedAt: String = "",
    val expiresAt: String = "",
    val affectedAreas: List<String> = emptyList()
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
    val publishTime: String,
    val station: StationInfo? = null
)

enum class WeatherSource { FORECAST, OBSERVATIONS, AIR, ALERTS }

data class SourceIssue(val source: WeatherSource, val message: String)

data class CacheState(
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val sources: Set<WeatherSource> = emptySet()
)

data class ApiKeys(val cwa: String, val moenv: String = "")

data class WeatherReport(
    val place: Place,
    val current: CurrentWeather,
    val forecast: List<DailyForecast>,
    val airQuality: AirQuality?,
    val updatedAt: String,
    val hourly: List<HourlyForecast> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
    val issues: List<SourceIssue> = emptyList(),
    val cache: CacheState = CacheState(),
    val updatedEpochMillis: Long = 0L
)

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Success(val report: WeatherReport) : LoadState
    data class Error(val message: String) : LoadState
}

package tw.app.taiwanweather.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SunTimes(val sunrise: Instant, val sunset: Instant, val daylightTrend: String? = null) {
    fun sunriseText(): String = sunrise.atZone(TAIPEI_ZONE).format(TIME_FORMAT)
    fun sunsetText(): String = sunset.atZone(TAIPEI_ZONE).format(TIME_FORMAT)
    fun daylightText(): String {
        val minutes = (sunset.epochSecond - sunrise.epochSecond) / 60
        return "${minutes / 60} 小時 ${minutes % 60} 分"
    }
}

object SunCalculator {
    fun calculate(date: LocalDate, point: GeoPoint): SunTimes? {
        val sunrise = event(date, point, sunrise = true) ?: return null
        val sunset = event(date, point, sunrise = false) ?: return null
        val yesterdaySunrise = event(date.minusDays(1), point, sunrise = true)
        val yesterdaySunset = event(date.minusDays(1), point, sunrise = false)
        val todayMinutes = sunset.epochSecond - sunrise.epochSecond
        val yesterdayMinutes = yesterdaySunset?.epochSecond?.minus(yesterdaySunrise?.epochSecond ?: return null)
        val trend = yesterdayMinutes?.let {
            when {
                todayMinutes > it -> "漸增"
                todayMinutes < it -> "漸減"
                else -> "持平"
            }
        }
        return SunTimes(sunrise, sunset, trend)
    }

    fun isDark(now: Instant, times: SunTimes): Boolean = now < times.sunrise || now >= times.sunset

    private fun event(date: LocalDate, point: GeoPoint, sunrise: Boolean): Instant? {
        val day = date.dayOfYear.toDouble()
        val longitudeHour = point.longitude / 15.0
        val approximateTime = day + ((if (sunrise) 6.0 else 18.0) - longitudeHour) / 24.0
        val meanAnomaly = 0.9856 * approximateTime - 3.289
        val trueLongitude = normalize(
            meanAnomaly + 1.916 * sinDegrees(meanAnomaly) + 0.020 * sinDegrees(2 * meanAnomaly) + 282.634
        )
        var rightAscension = normalize(Math.toDegrees(kotlin.math.atan(0.91764 * tanDegrees(trueLongitude))))
        rightAscension += kotlin.math.floor(trueLongitude / 90.0) * 90.0 - kotlin.math.floor(rightAscension / 90.0) * 90.0
        rightAscension /= 15.0

        val sinDeclination = 0.39782 * sinDegrees(trueLongitude)
        val cosDeclination = cos(asin(sinDeclination))
        val cosHour = (cosDegrees(ZENITH) - sinDeclination * sinDegrees(point.latitude)) /
            (cosDeclination * cosDegrees(point.latitude))
        if (cosHour !in -1.0..1.0) return null

        val hour = (if (sunrise) 360.0 - Math.toDegrees(acos(cosHour)) else Math.toDegrees(acos(cosHour))) / 15.0
        val localMeanTime = hour + rightAscension - 0.06571 * approximateTime - 6.622
        val utcHour = normalizeHours(localMeanTime - longitudeHour)
        var result = date.atStartOfDay(ZoneId.of("UTC")).toInstant().plusSeconds((utcHour * 3600).toLong())
        val localDate = result.atZone(TAIPEI_ZONE).toLocalDate()
        if (localDate.isAfter(date)) result = result.minusSeconds(24 * 60 * 60)
        if (localDate.isBefore(date)) result = result.plusSeconds(24 * 60 * 60)
        return result
    }

    private fun normalize(value: Double) = (value % 360.0 + 360.0) % 360.0
    private fun normalizeHours(value: Double) = (value % 24.0 + 24.0) % 24.0
    private fun sinDegrees(value: Double) = sin(Math.toRadians(value))
    private fun cosDegrees(value: Double) = cos(Math.toRadians(value))
    private fun tanDegrees(value: Double) = tan(Math.toRadians(value))

    private const val ZENITH = 90.833
}

val TAIPEI_ZONE: ZoneId = ZoneId.of("Asia/Taipei")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

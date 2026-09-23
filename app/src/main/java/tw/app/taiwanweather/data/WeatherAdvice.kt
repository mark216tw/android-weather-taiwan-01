package tw.app.taiwanweather.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AqiLevel { GOOD, MODERATE, SENSITIVE, UNHEALTHY, VERY_UNHEALTHY, HAZARDOUS }

data class AqiHealthAdvice(
    val level: AqiLevel,
    val label: String,
    val generalAdvice: String,
    val sensitiveAdvice: String,
    val maskAdvice: String
)

fun aqiHealthAdvice(aqi: String): AqiHealthAdvice? = when (aqi.toIntOrNull()) {
    in 0..50 -> AqiHealthAdvice(
        AqiLevel.GOOD,
        "良好",
        "空氣品質良好，適合正常戶外活動。",
        "敏感族群可正常進行戶外活動。",
        "一般情況不需因空品配戴口罩。"
    )
    in 51..100 -> AqiHealthAdvice(
        AqiLevel.MODERATE,
        "普通",
        "大多數人可正常進行戶外活動。",
        "極敏感族群若有不適，請減少長時間或劇烈活動。",
        "有呼吸道不適時可考慮配戴口罩。"
    )
    in 101..150 -> AqiHealthAdvice(
        AqiLevel.SENSITIVE,
        "對敏感族群不健康",
        "一般族群可活動，但請留意身體狀況。",
        "兒童、長者及心肺疾病患者應減少長時間或劇烈戶外活動。",
        "敏感族群外出建議配戴合適口罩。"
    )
    in 151..200 -> AqiHealthAdvice(
        AqiLevel.UNHEALTHY,
        "對所有族群不健康",
        "請減少長時間或劇烈戶外活動。",
        "敏感族群應避免戶外活動並留意症狀。",
        "必要外出時建議配戴合適口罩。"
    )
    in 201..300 -> AqiHealthAdvice(
        AqiLevel.VERY_UNHEALTHY,
        "非常不健康",
        "請避免戶外活動並減少開窗。",
        "敏感族群應留在室內，若不適請儘速就醫。",
        "必要外出時請配戴合適口罩。"
    )
    in 301..Int.MAX_VALUE -> AqiHealthAdvice(
        AqiLevel.HAZARDOUS,
        "危害",
        "所有人都應避免戶外活動並留在室內。",
        "敏感族群應嚴格避免外出，出現症狀請立即就醫。",
        "不得已外出時請配戴合適口罩。"
    )
    else -> null
}

fun todayWeatherSummary(report: WeatherReport): String {
    val hourly = report.hourly.next24Hours()
    val afternoonRain = hourly.filter { it.startTime.taipeiHour() in 12..17 }
        .mapNotNull { it.rainProbability.toIntOrNull() }.maxOrNull()
    val maxRain = hourly.mapNotNull { it.rainProbability.toIntOrNull() }.maxOrNull()
        ?: report.current.rainProbability.toIntOrNull()
    val temperatures = hourly.mapNotNull { it.temperature.toIntOrNull() }
    val maxTemperature = temperatures.maxOrNull() ?: report.current.temperature.toIntOrNull()
    val minTemperature = temperatures.minOrNull() ?: report.current.temperature.toIntOrNull()
    val maxWind = hourly.mapNotNull { Regex("-?\\d+(?:\\.\\d+)?").find(it.wind)?.value?.toDoubleOrNull() }.maxOrNull()

    return when {
        afternoonRain != null && afternoonRain >= 60 -> "午後降雨機率高，外出記得帶傘。"
        maxRain != null && maxRain >= 60 -> "今日降雨機率高，外出記得攜帶雨具。"
        maxRain != null && maxRain >= 30 -> "今日可能有雨，建議隨身攜帶雨具。"
        maxTemperature != null && maxTemperature >= 36 -> "今日高溫炎熱，記得補充水分並做好防曬。"
        minTemperature != null && minTemperature <= 10 -> "今日氣溫偏低，外出請注意保暖。"
        maxWind != null && maxWind >= 10 -> "今日風勢較強，外出請留意強風與掉落物。"
        hourly.any { it.description.contains("雨") } || report.current.description.contains("雨") ->
            "今日有降雨機會，外出建議攜帶雨具。"
        else -> "今日天氣大致穩定，出門前仍請留意最新預報。"
    }
}

private fun List<HourlyForecast>.next24Hours(): List<HourlyForecast> {
    val first = firstNotNullOfOrNull { it.startTime.toInstantOrNull() } ?: return take(8)
    val end = first.plusSeconds(24 * 60 * 60)
    return filter { forecast ->
        forecast.startTime.toInstantOrNull()?.let { !it.isBefore(first) && it.isBefore(end) } ?: false
    }
}

private fun String.taipeiHour(): Int? = toInstantOrNull()?.atZone(TAIPEI)?.hour

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
    ?: runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
    ?: runCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(TAIPEI).toInstant()
    }.getOrNull()

private val TAIPEI = ZoneId.of("Asia/Taipei")

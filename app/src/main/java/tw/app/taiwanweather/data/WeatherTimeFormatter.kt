package tw.app.taiwanweather.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val taipeiZone = ZoneId.of("Asia/Taipei")
private val hourlyDisplayFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

fun formatHourlyTime(value: String): String {
    val zoned = runCatching { OffsetDateTime.parse(value).toInstant().atZone(taipeiZone) }.getOrNull()
        ?: runCatching { Instant.parse(value).atZone(taipeiZone) }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(taipeiZone)
        }.getOrNull()
    if (zoned != null) return zoned.format(hourlyDisplayFormatter)

    return Regex("(\\d{2}-\\d{2})[T ](\\d{2}:\\d{2})")
        .find(value)
        ?.let { "${it.groupValues[1].replace('-', '/')} ${it.groupValues[2]}" }
        ?: value
}

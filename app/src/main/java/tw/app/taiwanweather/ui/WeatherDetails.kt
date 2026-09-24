package tw.app.taiwanweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.app.taiwanweather.data.WeatherReport
import tw.app.taiwanweather.data.uvProtectionAdvice

@Composable
internal fun WeatherDetailsSection(report: WeatherReport) {
    val today = report.forecast.firstOrNull()
    val uv = report.hourly.firstNotNullOfOrNull { it.uvIndex.takeUnless(::missing) }
        ?: today?.uvIndex?.takeUnless(::missing)
    val uvAdvice = uv?.let(::uvProtectionAdvice)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val comfort = report.hourly.firstNotNullOfOrNull { it.comfort.takeUnless(::missing) }
        ?: today?.comfort.orEmpty()
    val details = listOfNotNull(
        detail("露點", report.current.dewPoint, "°C"),
        detail("氣壓", report.current.pressure, " hPa"),
        detail("目前／累積雨量", report.current.precipitation, " mm"),
        detail("最大陣風", report.current.gustSpeed, " m/s"),
        detail("蒲福風級", report.current.beaufortScale, " 級"),
        detail("最低／最高溫", range(today?.minTemperature, today?.maxTemperature), "°C"),
        detail("最低／最高體感", range(today?.minApparentTemperature, today?.maxApparentTemperature), "°C"),
        detail("舒適度", comfort, "")
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("今日生活氣象", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (uv != null) {
                Text("紫外線 $uv · ${uvAdvice?.label ?: "資料可用"}", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                uvAdvice?.let { Text(it.advice, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Text("紫外線目前無資料", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收合詳細氣象" else "查看詳細氣象")
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                if (details.isEmpty()) Text("目前沒有更多詳細資料", color = MaterialTheme.colorScheme.onSurfaceVariant)
                details.forEach { (label, value) -> DetailRow(label, value) }
            }
        }
    }
}

@Composable
internal fun AirPollutantDetails(values: List<Pair<String, String>>, contentColor: Color) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "收合污染物資料" else "查看污染物詳細資料")
    }
    if (expanded) {
        HorizontalDivider(Modifier.padding(bottom = 8.dp), color = contentColor.copy(alpha = .25f))
        values.filterNot { missing(it.second) }.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = contentColor.copy(alpha = .8f))
                Text(value, fontWeight = FontWeight.Bold, color = contentColor)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun detail(label: String, value: String?, suffix: String): Pair<String, String>? =
    value?.takeUnless(::missing)?.let { label to "$it$suffix" }

private fun range(min: String?, max: String?): String =
    if (min == null || max == null || missing(min) || missing(max)) "--" else "$min / $max"

private fun missing(value: String) = value.isBlank() || value.startsWith("--")

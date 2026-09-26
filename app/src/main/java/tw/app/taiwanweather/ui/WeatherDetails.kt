package tw.app.taiwanweather.ui

import androidx.compose.foundation.BorderStroke
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
import tw.app.taiwanweather.data.SunTimes
import tw.app.taiwanweather.data.uvProtectionAdvice

@Composable
internal fun WeatherDetailsSection(report: WeatherReport, sunTimes: SunTimes?, forceExpanded: Boolean = false) {
    val today = report.forecast.firstOrNull()
    val uv = report.hourly.firstNotNullOfOrNull { it.uvIndex.takeUnless(::missing) }
        ?: today?.uvIndex?.takeUnless(::missing)
    val uvAdvice = uv?.let(::uvProtectionAdvice)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val details = listOfNotNull(
        detail("露點", report.current.dewPoint, "°C"),
        detail("氣壓", report.current.pressure, " hPa"),
        detail("平均風速／風向", report.current.wind, ""),
        detail("最大陣風", report.current.gustSpeed, " m/s"),
        detail("1 小時累積雨量", report.current.rainfall1Hour, " mm"),
        detail("3 小時累積雨量", report.current.rainfall3Hours, " mm"),
        detail("24 小時累積雨量", report.current.rainfall24Hours, " mm"),
        detail("今日日照時數", report.current.sunshineDuration, " 小時"),
        detail("最低／最高溫", range(today?.minTemperature, today?.maxTemperature), "°C"),
        detail("最低／最高體感", range(today?.minApparentTemperature, today?.maxApparentTemperature), "°C"),
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("今日生活氣象", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (uv != null) {
                Text("紫外線 $uv · ${uvAdvice?.label ?: "資料可用"}", Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                uvAdvice?.let {
                    Text(
                        it.advice,
                        Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f)
                    )
                }
            } else {
                Text(
                    "紫外線目前無資料",
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f)
                )
            }
            if (!forceExpanded) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收合詳細氣象" else "查看詳細氣象")
                }
            }
            if (forceExpanded || expanded) {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                sunTimes?.let {
                    DetailRow("日出", it.sunriseText())
                    DetailRow("日落", it.sunsetText())
                }
                if (details.isEmpty()) {
                    Text("目前沒有更多詳細資料", color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f))
                }
                details.forEach { (label, value) -> DetailRow(label, value, MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
}

@Composable
internal fun AirPollutantDetails(
    values: List<Pair<String, String>>,
    contentColor: Color,
    forceExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!forceExpanded) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收合污染物資料" else "查看污染物詳細資料")
        }
    }
    if (forceExpanded || expanded) {
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
private fun DetailRow(label: String, value: String, contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = contentColor.copy(alpha = .8f))
        Text(value, fontWeight = FontWeight.Bold, color = contentColor)
    }
}

private fun detail(label: String, value: String?, suffix: String): Pair<String, String>? =
    value?.takeUnless(::missing)?.let { label to "$it$suffix" }

private fun range(min: String?, max: String?): String =
    if (min == null || max == null || missing(min) || missing(max)) "--" else "$min / $max"

private fun missing(value: String) = value.isBlank() || value.startsWith("--")

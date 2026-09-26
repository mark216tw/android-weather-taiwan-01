package tw.app.taiwanweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tw.app.taiwanweather.AppUiState
import tw.app.taiwanweather.AppViewModel
import tw.app.taiwanweather.ApiTestState
import tw.app.taiwanweather.data.DailyForecast
import tw.app.taiwanweather.data.AqiLevel
import tw.app.taiwanweather.data.AirQuality
import tw.app.taiwanweather.data.LoadState
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.SunTimes
import tw.app.taiwanweather.data.TaiwanCounties
import tw.app.taiwanweather.data.WeatherReport
import tw.app.taiwanweather.data.aqiHealthAdvice
import tw.app.taiwanweather.data.beaufortName
import tw.app.taiwanweather.data.comfortDescription
import tw.app.taiwanweather.data.todayWeatherSummary

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
fun TaiwanWeatherApp(viewModel: AppViewModel, requestLocation: () -> Unit) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var draftCounty by remember { mutableStateOf(state.selected.county) }
    var draftTownship by remember { mutableStateOf(state.selected.township) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    BackHandler(enabled = screen != AppScreen.Home) {
        screen = when (screen) {
            AppScreen.CountyPicker, AppScreen.TownshipPicker -> AppScreen.Locations
            AppScreen.Locations, AppScreen.Settings -> AppScreen.Home
            AppScreen.Home -> AppScreen.Home
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .34f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .24f)
                    )
                )
            )
        ) {
            when (screen) {
                AppScreen.Home -> HomeScreen(
                    state = state,
                    refresh = viewModel::refresh,
                    locate = requestLocation,
                    chooseLocation = { draftCounty = state.selected.county; draftTownship = state.selected.township; screen = AppScreen.Locations },
                    openSettings = { screen = AppScreen.Settings },
                    share = {
                        val report = (state.loadState as? LoadState.Success)?.report ?: return@HomeScreen
                        scope.launch {
                            sharing = true
                            try {
                                shareWeatherPage(
                                    context,
                                    report,
                                    state.sunTimes,
                                    state.isDarkBySun == true,
                                    darkTheme
                                )
                            } catch (error: Throwable) {
                                snackbar.showSnackbar("無法分享天氣頁面：${error.message ?: "未知錯誤"}")
                            } finally {
                                sharing = false
                            }
                        }
                    },
                    isSharing = sharing
                )
                AppScreen.Locations -> LocationAndFavoritesScreen(
                    state,
                    onBack = { screen = AppScreen.Home },
                    county = draftCounty,
                    township = draftTownship,
                    onCountyClick = { screen = AppScreen.CountyPicker },
                    onTownshipClick = { viewModel.loadTownships(draftCounty); screen = AppScreen.TownshipPicker },
                    onSelectCounty = { draftCounty = it; draftTownship = TaiwanCounties.first { county -> county.name == it }.defaultTownship; viewModel.loadTownships(it); screen = AppScreen.Locations },
                    onSelectTownship = { draftTownship = it; screen = AppScreen.Locations },
                    onSelect = { viewModel.select(it); screen = AppScreen.Home },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRemove = viewModel::removeFavorite,
                    onMove = viewModel::moveFavorite
                )
                AppScreen.Settings -> SettingsScreen(
                    state,
                    { screen = AppScreen.Home },
                    viewModel::saveKeys,
                    viewModel::testCwa,
                    viewModel::testMoenv,
                    viewModel::resetCwaTest,
                    viewModel::resetMoenvTest
                )
                AppScreen.CountyPicker -> LocationPickerScreen("選擇縣市", TaiwanCounties.map { it.name }, draftCounty, { draftCounty = it; draftTownship = TaiwanCounties.first { c -> c.name == it }.defaultTownship; viewModel.loadTownships(it); screen = AppScreen.Locations }, { screen = AppScreen.Locations })
                AppScreen.TownshipPicker -> LocationPickerScreen("選擇鄉鎮市區", state.townships, draftTownship, { draftTownship = it; screen = AppScreen.Locations }, { screen = AppScreen.Locations })
            }
        }
    }
}

private enum class AppScreen { Home, Locations, Settings, CountyPicker, TownshipPicker }

@Composable
internal fun HomeScreen(
    state: AppUiState,
    refresh: () -> Unit,
    locate: () -> Unit,
    chooseLocation: () -> Unit,
    openSettings: () -> Unit,
    share: () -> Unit = {},
    isSharing: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Text("台灣天氣", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = refresh, enabled = !state.isRefreshing, modifier = Modifier.offset(y = (-8).dp)) {
                        if (state.isRefreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "重新整理")
                    }
                    IconButton(
                        onClick = share,
                        enabled = state.loadState is LoadState.Success && !isSharing,
                        modifier = Modifier.offset(y = (-8).dp)
                    ) {
                        if (isSharing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Share, "分享完整天氣頁面")
                    }
                    IconButton(openSettings, modifier = Modifier.offset(y = (-8).dp)) { Icon(Icons.Default.Settings, "設定") }
                }
                Text(
                    "今天也要帶著好心情出門喔 ♡",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = chooseLocation, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddLocationAlt, null)
                    Text(" ${state.selected.title}")
                }
                IconButton(locate) { Icon(Icons.Default.LocationOn, "使用目前位置", tint = MaterialTheme.colorScheme.secondary) }
            }
        }
        when (val load = state.loadState) {
            LoadState.Idle -> item { EmptyCard("請先到設定輸入 API 授權碼，再開始查看天氣。") }
            LoadState.Loading -> item { Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is LoadState.Error -> item {
                EmptyCard(load.message)
                Spacer(Modifier.height(8.dp))
                Button(onClick = refresh, modifier = Modifier.fillMaxWidth()) { Text("再試一次") }
            }
            is LoadState.Success -> reportItems(
                load.report,
                state.sunTimes,
                state.isDarkBySun == true,
                state.refreshError,
                state.moenvKey.isBlank(),
                refresh,
                openSettings
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(
    report: WeatherReport,
    sunTimes: SunTimes?,
    isNight: Boolean,
    refreshError: String?,
    missingMoenvKey: Boolean,
    refresh: () -> Unit,
    openSettings: () -> Unit
) {
    if (report.cache.fromCache) item {
        CuteCard {
            Text(
                if (report.cache.stale) "目前顯示已儲存資料，內容可能已過期" else "目前顯示已儲存資料",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
    refreshError?.let { message -> item {
        CuteCard {
            Text("更新失敗：$message", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = refresh) { Text("再試一次") }
        }
    } }
    if (report.alerts.isNotEmpty()) {
        item { Text("氣象警特報", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(report.alerts, key = { it.id }) { alert ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(" ${alert.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (alert.description.isNotBlank()) Text(alert.description, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    val period = listOf(alert.issuedAt, alert.expiresAt).filter(String::isNotBlank).joinToString(" - ")
                    if (period.isNotBlank()) Text(period, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
    item { CurrentWeatherCard(report, sunTimes, isNight) }
    item { WeatherDetailsSection(report, sunTimes) }
    if (missingMoenvKey) item {
        CuteCard {
            Text("尚未設定環境部 API Key", fontWeight = FontWeight.Bold)
            Text("設定後即可查看最近測站的空氣品質。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = openSettings) { Text("前往設定") }
        }
    }
    report.airQuality?.let { air ->
        item { AirQualityCard(air) }
    }
    if (report.issues.isNotEmpty()) item {
        CuteCard {
            Text("部分資料無法更新", fontWeight = FontWeight.Bold)
            report.issues.forEach { Text("${it.source.name}: ${it.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    item { Text("未來幾天", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    items(report.forecast) { ForecastCard(it) }
    item {
        Column {
            Text("更新於 ${report.updatedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("資料來源：中央氣象署、環境部", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ShareHomePage(report: WeatherReport, sunTimes: SunTimes?, isNight: Boolean) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .34f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .24f)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("台灣天氣", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(report.place.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (report.cache.fromCache) {
                CuteCard {
                    Text(
                        if (report.cache.stale) "目前顯示已儲存資料，內容可能已過期" else "目前顯示已儲存資料",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (report.alerts.isNotEmpty()) {
                Text("氣象警特報", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                report.alerts.forEach { alert ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(" ${alert.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            if (alert.description.isNotBlank()) {
                                Text(alert.description, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            val period = listOf(alert.issuedAt, alert.expiresAt).filter(String::isNotBlank).joinToString(" - ")
                            if (period.isNotBlank()) Text(period, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            CurrentWeatherCard(report, sunTimes, isNight)
            WeatherDetailsSection(report, sunTimes, forceExpanded = true)
            report.airQuality?.let { AirQualityCard(it, forceExpanded = true) }
            if (report.issues.isNotEmpty()) {
                CuteCard {
                    Text("部分資料無法更新", fontWeight = FontWeight.Bold)
                    report.issues.forEach {
                        Text("${it.source.name}: ${it.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("未來幾天", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            report.forecast.forEach { ForecastCard(it) }
            Text("更新於 ${report.updatedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("資料來源：中央氣象署、環境部", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CurrentWeatherCard(report: WeatherReport, sunTimes: SunTimes?, isNight: Boolean) {
    val current = report.current
    val comfort = comfortDescription(current.comfort, current.temperature)
    val beaufort = beaufortName(current.beaufortScale)
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(weatherSymbol(current.description, isNight), fontSize = 66.sp, modifier = Modifier.padding(end = 12.dp))
                Text("${current.temperature}°", fontSize = 68.sp, fontWeight = FontWeight.Black)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 12.dp).widthIn(min = 88.dp)
                ) {
                    Metric("濕度", "${current.humidity}%", singleLine = true)
                    Metric("體感", "${current.apparentTemperature}°", singleLine = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(current.description, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                comfort?.let {
                    Text("　$it", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .14f))
            val rainfall = current.precipitation.takeUnless(::missingWeatherValue)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric("降雨機率", "${current.rainProbability}%", Modifier.weight(1f))
                rainfall?.let { Metric("今日累積雨量", "$it mm", Modifier.weight(1f)) }
            }
            if (sunTimes != null) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .14f))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val beaufortText = if (missingWeatherValue(current.beaufortScale)) "--"
                        else if (beaufort == null) "${current.beaufortScale} 級" else "${current.beaufortScale} 級 · $beaufort"
                    Metric("蒲福風級", beaufortText)
                    Metric("今日白晝", "${sunTimes.daylightText()} · ${sunTimes.daylightTrend.orEmpty()}")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .14f))
            Text(
                todayWeatherSummary(report),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            report.current.station?.let { station ->
                val distance = station.distanceKm?.let { " · %.1f 公里".format(it) }.orEmpty()
                val time = station.observedAt.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
                Text("${station.name}測站$time$distance", Modifier.padding(top = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
            }
        }
    }
}

@Composable
private fun AirQualityCard(air: AirQuality, forceExpanded: Boolean = false) {
    val advice = aqiHealthAdvice(air.aqi)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val container = advice?.level?.aqiColor(dark) ?: MaterialTheme.colorScheme.surface
    val content = if (container.luminance() > .179f) Color.Black else Color.White
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(content.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Air, null, tint = content)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("空氣品質 ${advice?.label ?: air.status}", fontWeight = FontWeight.Bold, color = content)
                    Text("AQI ${air.aqi}　PM2.5 ${air.pm25}", color = content)
                    val distance = air.station?.distanceKm?.let { " · %.1f 公里".format(it) }.orEmpty()
                    Text("${air.siteName}測站 · ${air.publishTime}$distance", fontSize = 12.sp, color = content.copy(alpha = .8f))
                }
            }
            advice?.let {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = content.copy(alpha = .25f))
                Text(it.generalAdvice, fontWeight = FontWeight.Bold, color = content)
                Text("敏感族群：${it.sensitiveAdvice}", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = content)
                Text(it.maskAdvice, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = content)
            }
            AirPollutantDetails(
                values = listOf(
                    "主要污染物" to air.pollutant,
                    "PM10" to "${air.pm10} μg/m³",
                    "PM2.5 平均" to "${air.pm25Average} μg/m³",
                    "PM10 平均" to "${air.pm10Average} μg/m³",
                    "臭氧 O₃" to "${air.o3} ppb",
                    "臭氧 8 小時" to "${air.o3_8hr} ppb",
                    "一氧化碳 CO" to "${air.co} ppm",
                    "CO 8 小時" to "${air.co_8hr} ppm",
                    "二氧化硫 SO₂" to "${air.so2} ppb",
                    "二氧化氮 NO₂" to "${air.no2} ppb"
                ),
                contentColor = content,
                forceExpanded = forceExpanded
            )
        }
    }
}

private fun missingWeatherValue(value: String) = value.isBlank() || value == "--"

internal fun AqiLevel.aqiColor(dark: Boolean) = if (dark) {
    when (this) {
        AqiLevel.GOOD -> Color(0xFF245C3D)
        AqiLevel.MODERATE -> Color(0xFF665D20)
        AqiLevel.SENSITIVE -> Color(0xFF714A1E)
        AqiLevel.UNHEALTHY -> Color(0xFF702D2D)
        AqiLevel.VERY_UNHEALTHY -> Color(0xFF593259)
        AqiLevel.HAZARDOUS -> Color(0xFF4D2830)
    }
} else {
    when (this) {
        AqiLevel.GOOD -> Color(0xFF00E800)
        AqiLevel.MODERATE -> Color(0xFFFFFF00)
        AqiLevel.SENSITIVE -> Color(0xFFFF7E00)
        AqiLevel.UNHEALTHY -> Color(0xFFFF0000)
        AqiLevel.VERY_UNHEALTHY -> Color(0xFF8F3F97)
        AqiLevel.HAZARDOUS -> Color(0xFF7E0023)
    }
}

@Composable private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Bold, maxLines = if (singleLine) 1 else Int.MAX_VALUE, softWrap = !singleLine)
}

@Composable
private fun ForecastCard(day: DailyForecast) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.date.drop(5).replace('-', '/'), fontWeight = FontWeight.Bold)
                Text(weatherSymbol(day.description), fontSize = 34.sp)
            }
            Column(Modifier.weight(.48f).padding(horizontal = 12.dp)) {
                Text(day.description, fontWeight = FontWeight.Bold)
                if (day.comfort != "--") Text(day.comfort, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (day.uvIndex != "--") Text("紫外線 ${day.uvIndex}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(.3f), horizontalAlignment = Alignment.End) {
                Text("${day.minTemperature}° / ${day.maxTemperature}°", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Umbrella, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(" ${day.rainProbability}%", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LocationAndFavoritesScreen(
    state: AppUiState,
    onBack: () -> Unit,
    county: String,
    township: String,
    onCountyClick: () -> Unit,
    onTownshipClick: () -> Unit,
    onSelectCounty: (String) -> Unit,
    onSelectTownship: (String) -> Unit,
    onSelect: (Place) -> Unit,
    onToggleFavorite: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val draftPlace = Place(county, township)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "回到主畫面") }
                Column {
                    Text("地點與收藏", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("選擇台灣縣市與鄉鎮市區", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            CuteCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(onCountyClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("縣市", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(county, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.ArrowDropDown, "選擇縣市")
                        }
                    OutlinedButton(
                            onClick = onTownshipClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.loadingTownships,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("鄉鎮市區", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (state.loadingTownships) "載入中…" else township, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                            if (state.loadingTownships) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.ArrowDropDown, "選擇鄉鎮市區")
                        }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({ onSelect(draftPlace) }, enabled = !state.loadingTownships, modifier = Modifier.weight(1f)) {
                            Text("查看天氣")
                        }
                        OutlinedButton({ onToggleFavorite(draftPlace) }, modifier = Modifier.weight(1f)) {
                            Icon(if (draftPlace in state.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            Text(if (draftPlace in state.favorites) " 移除收藏" else " 加入收藏")
                        }
                    }
                }
            }
        }
        item { Text("我的收藏", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        if (state.favorites.isEmpty()) item { EmptyCard("還沒有收藏地點，將喜歡的地方放進口袋吧！") }
        items(state.favorites, key = { it.title }) { place ->
            val index = state.favorites.indexOf(place)
            CuteCard(onClick = { onSelect(place) }) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Text(place.title, Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Bold)
                Column {
                    Row {
                        IconButton({ onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "上移 ${place.title}") }
                        IconButton({ onMove(index, index + 1) }, enabled = index < state.favorites.lastIndex) { Icon(Icons.Default.ArrowDownward, "下移 ${place.title}") }
                    }
                }
                IconButton({ onRemove(place) }) { Icon(Icons.Default.DeleteOutline, "刪除") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    save: (String, String) -> Unit,
    testCwa: (String) -> Unit,
    testMoenv: (String) -> Unit,
    resetCwaTest: () -> Unit,
    resetMoenvTest: () -> Unit
) {
    var cwa by remember(state.cwaKey) { mutableStateOf(state.cwaKey) }
    var moenv by remember(state.moenvKey) { mutableStateOf(state.moenvKey) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "回到主畫面") }
                Text("設定", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
        item { Text("授權碼只會加密儲存在這台裝置，不會傳送到其他伺服器。") }
        item {
            ApiKeyCard("中央氣象署 CWA", "申請中央氣象署授權碼", "https://opendata.cwa.gov.tw/index", cwa, {
                cwa = it
                resetCwaTest()
            }, state.cwaTestState, { testCwa(cwa) })
        }
        item {
            ApiKeyCard("環境部 MOENV", "申請環境部 API Key", "https://data.moenv.gov.tw/api-term", moenv, {
                moenv = it
                resetMoenvTest()
            }, state.moenvTestState, { testMoenv(moenv) })
        }
        item { Button({ save(cwa, moenv) }, Modifier.fillMaxWidth()) { Text("儲存設定") } }
        item {
            CuteCard {
                Column {
                    Text("資料與隱私", fontWeight = FontWeight.Bold)
                    Text("定位只用來辨識台灣行政區，不會在背景持續定位。拒絕定位後仍可手動選擇地點。", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ApiKeyCard(title: String, applyLabel: String, applyUrl: String, value: String, onChange: (String) -> Unit, testState: ApiTestState, test: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    CuteCard {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (testState is ApiTestState.Available) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(" 可用", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(applyUrl))) }, contentPadding = PaddingValues(0.dp)) { Text(applyLabel) }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                label = { Text("API 授權碼") },
                trailingIcon = {
                    Row {
                        IconButton({ visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "隱藏授權碼" else "顯示授權碼")
                        }
                        if (value.isNotEmpty()) {
                            IconButton({ onChange("") }) { Icon(Icons.Default.Cancel, "清除授權碼") }
                        }
                    }
                }
            )
            if (testState is ApiTestState.Failed) {
                Text(testState.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            OutlinedButton(test, enabled = testState !is ApiTestState.Testing && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (testState is ApiTestState.Testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("連線測試")
            }
        }
    }
}

@Composable
private fun CuteCard(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, content = content) }
}

@Composable
private fun LocationPickerScreen(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    var query by remember(title) { mutableStateOf("") }
    val filtered = options.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜尋") }, placeholder = { Text("輸入名稱篩選") })
        }
        items(filtered) { item ->
            Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item, Modifier.weight(1f), fontSize = 17.sp, fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Normal)
                    if (item == selected) Icon(Icons.Default.Check, "已選擇", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable private fun EmptyCard(text: String) = CuteCard {
    Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun weatherSymbol(description: String, isNight: Boolean = false): String = when {
    "雷" in description -> "⛈"
    "雨" in description -> "🌧"
    "雪" in description -> "🌨"
    isNight && "晴" in description -> "🌙"
    "晴" in description && "雲" in description -> "🌤"
    "晴" in description -> "☀"
    "陰" in description -> "☁"
    else -> "🌥"
}

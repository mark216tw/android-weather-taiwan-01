package tw.app.taiwanweather.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import tw.app.taiwanweather.data.DisplayMode

private val SakuraColors = lightColorScheme(
    primary = Color(0xFFD95F8D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E5),
    onPrimaryContainer = Color(0xFF5B1130),
    secondary = Color(0xFF5F8F91),
    secondaryContainer = Color(0xFFCBEDEE),
    tertiary = Color(0xFF9A6B2F),
    background = Color(0xFFFFF7FA),
    surface = Color(0xFFFFFBFC),
    surfaceVariant = Color(0xFFF8E8EE),
    error = Color(0xFFBA1A1A)
)

private val MidnightSakuraColors = darkColorScheme(
    primary = Color(0xFFFFA8C4),
    onPrimary = Color(0xFF601333),
    primaryContainer = Color(0xFF7D2949),
    onPrimaryContainer = Color(0xFFFFD9E5),
    secondary = Color(0xFFA9D5D5),
    onSecondary = Color(0xFF143738),
    secondaryContainer = Color(0xFF2E4F50),
    onSecondaryContainer = Color(0xFFCBEDEE),
    tertiary = Color(0xFFFFD18E),
    background = Color(0xFF171217),
    onBackground = Color(0xFFF0E1E6),
    surface = Color(0xFF211A20),
    onSurface = Color(0xFFF0E1E6),
    surfaceVariant = Color(0xFF4F4248),
    onSurfaceVariant = Color(0xFFD5C1C8),
    error = Color(0xFFFFB4AB)
)

@Composable
fun TaiwanWeatherTheme(mode: DisplayMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        DisplayMode.SYSTEM -> isSystemInDarkTheme()
        DisplayMode.LIGHT -> false
        DisplayMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) MidnightSakuraColors else SakuraColors, content = content)
}

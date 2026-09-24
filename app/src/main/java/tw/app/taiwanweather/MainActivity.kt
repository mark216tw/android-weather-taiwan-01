package tw.app.taiwanweather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import tw.app.taiwanweather.ui.TaiwanWeatherApp
import tw.app.taiwanweather.ui.TaiwanWeatherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val state by viewModel.ui.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = state.isDarkBySun ?: systemDark
            TaiwanWeatherTheme(dark) {
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(0xFF171217.toInt()) else SystemBarStyle.light(0xFFFFF7FA.toInt(), 0xFF171217.toInt()),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(0xFF211A20.toInt()) else SystemBarStyle.light(0xFFFFFBFC.toInt(), 0xFF211A20.toInt())
                    )
                }
                LocationAwareApp(viewModel)
            }
        }
    }
}

@Composable
private fun LocationAwareApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.locate()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onForeground()
    }
    TaiwanWeatherApp(
        viewModel = viewModel,
        requestLocation = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.locate() else launcher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    )
}

package tw.app.taiwanweather.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tw.app.taiwanweather.data.SunTimes
import tw.app.taiwanweather.data.WeatherReport

internal suspend fun shareWeatherPage(
    context: Context,
    report: WeatherReport,
    sunTimes: SunTimes?,
    isNight: Boolean,
    darkTheme: Boolean
) {
    val bitmap = withContext(Dispatchers.Main.immediate) {
        renderWeatherPage(context, report, sunTimes, isNight, darkTheme)
    }
    val uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val image = File(directory, "taiwan-weather.png")
        FileOutputStream(image).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "無法建立分享圖片" }
        }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("台灣天氣", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享完整天氣頁面"))
}

private suspend fun renderWeatherPage(
    context: Context,
    report: WeatherReport,
    sunTimes: SunTimes?,
    isNight: Boolean,
    darkTheme: Boolean
): Bitmap = suspendCancellableCoroutine { continuation ->
    val activity = context.findActivity()
    if (activity == null) {
        continuation.resumeWithException(IllegalStateException("無法取得目前畫面"))
        return@suspendCancellableCoroutine
    }
    val parent = activity.window.decorView as ViewGroup
    val width = parent.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    val composeView = ComposeView(activity).apply {
        translationX = -(width * 2).toFloat()
        setContent {
            TaiwanWeatherTheme(darkTheme) {
                ShareHomePage(report, sunTimes, isNight)
            }
        }
    }
    parent.addView(composeView, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
    continuation.invokeOnCancellation { parent.removeViewSafely(composeView) }
    composeView.post {
        try {
            composeView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val height = composeView.measuredHeight
            require(height in 1..MAX_SHARE_HEIGHT) { "分享頁面高度超出限制" }
            composeView.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(bitmap))
            parent.removeViewSafely(composeView)
            continuation.resume(bitmap)
        } catch (error: Throwable) {
            parent.removeViewSafely(composeView)
            continuation.resumeWithException(error)
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun ViewGroup.removeViewSafely(view: View) {
    if (view.parent === this) removeView(view)
}

private const val MAX_SHARE_HEIGHT = 24_000

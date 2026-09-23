package tw.app.taiwanweather.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.TaiwanCounties
import java.util.Locale
import kotlin.coroutines.resume

class TaiwanLocationResolver(private val context: Context) {
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): Result<Place> = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = suspendCancellableCoroutine<Location?> { continuation ->
            client.lastLocation
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        } ?: error("無法取得位置，請確認已開啟定位服務")
        val addresses = Geocoder(context, Locale.TAIWAN).getFromLocation(location.latitude, location.longitude, 1)
        val address = addresses?.firstOrNull() ?: error("無法辨識目前行政區")
        if (!address.countryCode.equals("TW", ignoreCase = true)) error("目前不支援此地點天氣")
        val countyName = normalize(address.adminArea)
        val county = TaiwanCounties.firstOrNull { normalize(it.name) == countyName }
            ?: error("目前不支援此地點天氣")
        val township = normalize(address.subAdminArea ?: address.locality ?: address.subLocality)
        Place(county.name, township.ifBlank { county.defaultTownship })
    }

    private fun normalize(value: String?) = value.orEmpty().replace("台", "臺")
}

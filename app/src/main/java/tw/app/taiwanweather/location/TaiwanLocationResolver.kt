package tw.app.taiwanweather.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.app.taiwanweather.data.GeoPoint
import tw.app.taiwanweather.data.Place
import tw.app.taiwanweather.data.TaiwanCounties
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedPlace(val place: Place, val coordinate: GeoPoint)

class TaiwanLocationResolver(private val context: Context) {
    suspend fun coordinate(place: Place): GeoPoint? = runCatching {
        geocodeName("${place.county}${place.township}, Taiwan").firstOrNull()?.let {
            GeoPoint(it.latitude, it.longitude)
        }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): Result<ResolvedPlace> = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val last = suspendCancellableCoroutine<Location?> { continuation ->
            client.lastLocation
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
        val location = last?.takeIf { System.currentTimeMillis() - it.time <= MAX_LOCATION_AGE && it.accuracy <= MAX_ACCURACY }
            ?: suspendCancellableCoroutine { continuation ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
            }
            ?: error("無法取得位置，請確認已開啟定位服務")
        val addresses = geocode(location)
        val address = addresses?.firstOrNull() ?: error("無法辨識目前行政區")
        if (!address.countryCode.equals("TW", ignoreCase = true)) error("目前不支援此地點天氣")
        val countyName = normalize(address.adminArea)
        val county = TaiwanCounties.firstOrNull { normalize(it.name) == countyName }
            ?: error("目前不支援此地點天氣")
        val township = normalize(address.subAdminArea ?: address.locality ?: address.subLocality)
        ResolvedPlace(
            Place(county.name, township.ifBlank { county.defaultTownship }),
            GeoPoint(location.latitude, location.longitude)
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(location: Location) = if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { continuation ->
            Geocoder(context, Locale.TAIWAN).getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                if (continuation.isActive) continuation.resume(addresses)
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            Geocoder(context, Locale.TAIWAN).getFromLocation(location.latitude, location.longitude, 1)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocodeName(name: String) = if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { continuation ->
            Geocoder(context, Locale.TAIWAN).getFromLocationName(name, 1) { addresses ->
                if (continuation.isActive) continuation.resume(addresses)
            }
        }
    } else {
        withContext(Dispatchers.IO) { Geocoder(context, Locale.TAIWAN).getFromLocationName(name, 1).orEmpty() }
    }

    private fun normalize(value: String?) = value.orEmpty().replace("台", "臺")

    private companion object {
        const val MAX_LOCATION_AGE = 15 * 60_000L
        const val MAX_ACCURACY = 2_000f
    }
}

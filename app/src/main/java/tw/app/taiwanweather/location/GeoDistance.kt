package tw.app.taiwanweather.location

import tw.app.taiwanweather.data.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistance {
    fun kilometers(from: GeoPoint, to: GeoPoint): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 12_742.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

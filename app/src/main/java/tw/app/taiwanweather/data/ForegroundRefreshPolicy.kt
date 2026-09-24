package tw.app.taiwanweather.data

object ForegroundRefreshPolicy {
    const val UPDATE_INTERVAL_MILLIS = 30 * 60_000L
    const val RETRY_COOLDOWN_MILLIS = 5 * 60_000L

    fun shouldRefresh(
        lastSuccessfulUpdateMillis: Long?,
        lastAttemptMillis: Long?,
        nowMillis: Long,
        isRefreshing: Boolean
    ): Boolean {
        if (isRefreshing) return false
        if (lastAttemptMillis != null && nowMillis - lastAttemptMillis < RETRY_COOLDOWN_MILLIS) return false
        return lastSuccessfulUpdateMillis == null || nowMillis - lastSuccessfulUpdateMillis > UPDATE_INTERVAL_MILLIS
    }
}

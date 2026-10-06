package tech.granet.grove

/** Coalesce source events without replacing an active generation or repeatedly writing its marker. */
internal enum class IndexRefreshCause { MANUAL, PROVIDER_CHANGE, STALE_CACHE, REPAIR }
internal object IndexRefreshRequests {
    fun delayMillis(cause: IndexRefreshCause, lastStarted: Long, now: Long): Long {
        if (cause != IndexRefreshCause.PROVIDER_CHANGE || lastStarted <= 0) return 0
        return (30_000L - (now - lastStarted).coerceAtLeast(0)).coerceIn(0, 30_000)
    }
    fun request(active: String?, started: String?, pending: String?,
                schedule: () -> Boolean, defer: () -> Boolean): Boolean = when {
        active == null -> schedule()
        started != active || pending == active -> true
        else -> defer()
    }
}

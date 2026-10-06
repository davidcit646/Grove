package tech.granet.grove

/** Coalesce source events without replacing an active generation or repeatedly writing its marker. */
internal object IndexRefreshRequests {
    fun request(active: String?, started: String?, pending: String?,
                schedule: () -> Boolean, defer: () -> Boolean): Boolean = when {
        active == null -> schedule()
        started != active || pending == active -> true
        else -> defer()
    }
}

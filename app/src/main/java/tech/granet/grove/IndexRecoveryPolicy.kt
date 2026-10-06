package tech.granet.grove

internal object IndexRecoveryPolicy {
    fun release(expected: String, current: String?, present: Boolean, finished: Boolean): Boolean =
        expected == current && (!present || finished)
    fun repairAllowed(cause: IndexRefreshCause, attempted: Boolean): Boolean =
        cause == IndexRefreshCause.MANUAL || !attempted
}

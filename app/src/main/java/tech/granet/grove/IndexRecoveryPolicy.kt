package tech.granet.grove

internal object IndexRecoveryPolicy {
    fun release(expected: String, current: String?, present: Boolean, finished: Boolean): Boolean =
        PortablePolicy.ruleBool("indexRelease", "expected" to expected, "current" to current, "present" to present, "finished" to finished)
            ?: (expected == current && (!present || finished))
    fun repairAllowed(cause: IndexRefreshCause, attempted: Boolean): Boolean =
        PortablePolicy.ruleBool("repair", "manual" to (cause == IndexRefreshCause.MANUAL), "attempted" to attempted)
            ?: (cause == IndexRefreshCause.MANUAL || !attempted)
}

package tech.granet.grove

/** Home starts first; optional independent indexers reconcile after it is rendered. */
internal object StartupCoordinator {
    data class Plan(val loadApps: Boolean = false, val reconcileIndexes: Boolean = true)
    fun coldStart(): Plan = Plan(loadApps = true)
    fun resume(): Plan = Plan()
}

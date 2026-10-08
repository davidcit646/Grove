package tech.granet.grove

internal enum class CoreRecoveryReason {
    CONFIG,
    LAUNCHER_SERVICE,
    APP_CATALOG,
}

internal data class CoreRecoveryState(
    val reason: CoreRecoveryReason,
    val detail: String,
    val retryable: Boolean = true,
    val settingsEscape: Boolean = true,
)

internal object CoreRecoveryPolicy {
    fun forReason(reason: CoreRecoveryReason): CoreRecoveryState = CoreRecoveryState(
        reason = reason,
        detail = when (reason) {
            CoreRecoveryReason.CONFIG -> GroveErrorRegistry.CONFIG_LOAD.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings. Your saved settings have not been erased."
            }
            CoreRecoveryReason.LAUNCHER_SERVICE -> GroveErrorRegistry.LAUNCHER_SERVICE.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings."
            }
            CoreRecoveryReason.APP_CATALOG -> GroveErrorRegistry.APP_CATALOG.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings."
            }
        },
    )
}

package tech.granet.grove

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message

internal enum class ErrorSeverity(val label: String) {
    CONTINUE("Continue"),
    RECOVER("Recover"),
    STOP("Stop"),
}

internal data class GroveError(
    val code: Int,
    val gws: String,
    val feature: String,
    val severity: ErrorSeverity,
    val summary: String,
)

internal object GroveErrorRegistry {
    val CONFIG_IMPORT = GroveError(110, "GWS-config-import", "Configuration import", ErrorSeverity.CONTINUE,
        "The imported configuration could not be accepted.")
    val CONFIG_EXPORT = GroveError(111, "GWS-config-export", "Configuration export", ErrorSeverity.CONTINUE,
        "The configuration could not be exported.")
    val CONFIG_PERSIST = GroveError(112, "GWS-config-persist", "Configuration", ErrorSeverity.RECOVER,
        "Grove could not save the requested configuration change.")
    val CONFIG_LOAD = GroveError(113, "GWS-config-load", "Configuration", ErrorSeverity.RECOVER,
        "Grove could not load its saved configuration.")
    val APP_CATALOG = GroveError(210, "GWS-apps-catalog", "Installed apps", ErrorSeverity.RECOVER,
        "Android could not provide the installed app catalogue.")
    val LAUNCHER_SERVICE = GroveError(211, "GWS-apps-launcher-service", "Android launcher service", ErrorSeverity.RECOVER,
        "Grove could not connect to Android's launcher service.")
    val CONTACT_SEARCH = GroveError(220, "GWS-search-contacts", "Contact search", ErrorSeverity.CONTINUE,
        "The contacts provider could not be read.")
    val FILE_SEARCH = GroveError(230, "GWS-search-files", "File search", ErrorSeverity.CONTINUE,
        "Shared storage could not be searched completely.")
    val WALLPAPER_PREVIEW = GroveError(310, "GWS-wallpaper-preview", "Wallpaper preview", ErrorSeverity.CONTINUE,
        "This wallpaper preview is unavailable.")
    val WALLPAPER_APPLY = GroveError(311, "GWS-wallpaper-apply", "Wallpaper apply", ErrorSeverity.STOP,
        "Android did not confirm the wallpaper change.")
    val WALLPAPER_SYNC = GroveError(312, "GWS-wallpaper-sync", "Wallpaper preference", ErrorSeverity.RECOVER,
        "Android changed the wallpaper, but Grove could not save the matching Home preference.")
    val TUTORIAL_REPLAY = GroveError(410, "GWS-tutorial-replay", "Tutorial replay", ErrorSeverity.CONTINUE,
        "Tutorial replay could not start yet.")
    val GENERIC_NONFATAL = GroveError(500, "GWS-report-nonfatal", "Grove operation", ErrorSeverity.CONTINUE,
        "A caught Grove operation failed.")
    val REPORT_HANDOFF = GroveError(510, "GWS-report-handoff", "Problem report", ErrorSeverity.CONTINUE,
        "No mail app accepted the report draft.")
    val NATIVE_BRIDGE = GroveError(610, "GWS-native-bridge", "Native core", ErrorSeverity.CONTINUE,
        "The native helper was unavailable; Grove used its safe Kotlin fallback.")
    val UNCAUGHT_CRASH = GroveError(699, "GWS-runtime-crash", "Grove runtime", ErrorSeverity.STOP,
        "Grove stopped because of an uncaught error.")

    val all = listOf(
        CONFIG_IMPORT, CONFIG_EXPORT, CONFIG_PERSIST, CONFIG_LOAD, APP_CATALOG, LAUNCHER_SERVICE, CONTACT_SEARCH, FILE_SEARCH,
        WALLPAPER_PREVIEW, WALLPAPER_APPLY, WALLPAPER_SYNC, TUTORIAL_REPLAY, GENERIC_NONFATAL, REPORT_HANDOFF, NATIVE_BRIDGE, UNCAUGHT_CRASH,
    )

    fun byCode(code: Int): GroveError? = all.firstOrNull { it.code == code }
}

internal data class GroveErrorRoute(val dismissLabel: String, val actionLabel: String?)

internal object GroveErrorRouting {
    fun route(error: GroveError, hasAction: Boolean): GroveErrorRoute = GroveErrorRoute(
        dismissLabel = when (error.severity) {
            ErrorSeverity.CONTINUE -> "Continue"
            ErrorSeverity.RECOVER -> "Not now"
            ErrorSeverity.STOP -> "Close"
        },
        actionLabel = if (!hasAction) null else when (error.severity) {
            ErrorSeverity.RECOVER -> "Recover"
            else -> "Retry"
        },
    )
}

internal object GroveErrorPresenter {
    fun show(activity: Activity, error: GroveError, retry: (() -> Unit)? = null) {
        val body = "${error.feature} · ${error.severity.label}\nCode ${error.code} · ${error.gws}\n\n${error.summary}"
        val route = GroveErrorRouting.route(error, retry != null)
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle("Grove problem")
            .setMessage(body)
            .setNegativeButton(route.dismissLabel, null)
        if (retry != null) builder.setPositiveButton(route.actionLabel, null).create().also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener { retry() }
            }
            dialog.show()
            return
        }
        builder.setNeutralButton("Report") { _, _ ->
            CrashReporter.reportNonFatal(activity, error, null)
            activity.message("Problem report saved for your review")
            CrashReporter.promptIfPending(activity)
        }
        builder.show()
    }
}

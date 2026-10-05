package tech.granet.grove

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message

internal enum class GroveErrorOwner(val range: IntRange) {
    CONFIGURATION(100..199),
    APP_INVENTORY(200..299),
    UI_UX(300..399),
    SYSTEM(400..499),
    GFI(500..599),
    GCI(600..699),
}

internal enum class ErrorSeverity(val label: String) {
    DEGRADE("Degrade"),
    RECOVER("Recover"),
    STOP("Stop"),
}

internal data class GroveError(
    val code: Int,
    val owner: GroveErrorOwner,
    val gws: String?,
    val feature: String,
    val severity: ErrorSeverity,
    val summary: String,
) {
    fun codeLine(): String = buildString {
        append("Code ").append(code)
        gws?.let { append(" · ").append(it) }
    }
}

internal object GroveErrorRegistry {
    val CONFIG_IMPORT = GroveError(110, GroveErrorOwner.CONFIGURATION, null, "Configuration import", ErrorSeverity.DEGRADE,
        "The imported configuration could not be accepted.")
    val CONFIG_EXPORT = GroveError(111, GroveErrorOwner.CONFIGURATION, null, "Configuration export", ErrorSeverity.DEGRADE,
        "The configuration could not be exported.")
    val CONFIG_PERSIST = GroveError(112, GroveErrorOwner.CONFIGURATION, null, "Configuration", ErrorSeverity.RECOVER,
        "Grove could not save the requested configuration change.")
    val CONFIG_LOAD = GroveError(113, GroveErrorOwner.CONFIGURATION, null, "Configuration", ErrorSeverity.RECOVER,
        "Grove could not load its saved configuration.")

    val APP_CATALOG = GroveError(210, GroveErrorOwner.APP_INVENTORY, null, "Installed apps", ErrorSeverity.RECOVER,
        "Android could not provide the installed app catalogue.")
    val LAUNCHER_SERVICE = GroveError(211, GroveErrorOwner.APP_INVENTORY, null, "Android launcher service", ErrorSeverity.RECOVER,
        "Grove could not connect to Android's launcher service.")

    val WALLPAPER_PREVIEW = GroveError(310, GroveErrorOwner.UI_UX, "GWS-READ-01", "Wallpaper preview", ErrorSeverity.DEGRADE,
        "This wallpaper preview is unavailable.")
    val WALLPAPER_APPLY = GroveError(311, GroveErrorOwner.UI_UX, "GWS-APPLY-01", "Wallpaper apply", ErrorSeverity.STOP,
        "Android did not confirm the wallpaper change.")
    val WALLPAPER_SYNC = GroveError(312, GroveErrorOwner.UI_UX, "GWS-WRITE-01", "Wallpaper preference", ErrorSeverity.RECOVER,
        "Android changed the wallpaper, but Grove could not save the matching Home preference.")
    val WALLPAPER_LOCAL_SYNC = GroveError(313, GroveErrorOwner.UI_UX, "GWS-WRITE-02", "Custom wallpaper", ErrorSeverity.RECOVER,
        "Android changed the wallpaper, but Grove could not commit the selected custom image locally.")

    val TUTORIAL_REPLAY = GroveError(410, GroveErrorOwner.SYSTEM, null, "Tutorial replay", ErrorSeverity.DEGRADE,
        "Tutorial replay could not start yet.")
    val REPORT_HANDOFF = GroveError(420, GroveErrorOwner.SYSTEM, null, "Problem report", ErrorSeverity.DEGRADE,
        "No mail app accepted the report draft.")
    val NATIVE_BRIDGE = GroveError(430, GroveErrorOwner.SYSTEM, null, "Native core", ErrorSeverity.DEGRADE,
        "The native helper was unavailable; Grove used its safe Kotlin fallback.")
    val GENERIC_NONFATAL = GroveError(490, GroveErrorOwner.SYSTEM, null, "Grove operation", ErrorSeverity.DEGRADE,
        "A caught Grove operation failed.")
    val UNCAUGHT_CRASH = GroveError(499, GroveErrorOwner.SYSTEM, null, "Grove runtime", ErrorSeverity.STOP,
        "Grove stopped because of an uncaught error.")

    val FILE_SEARCH = GroveError(510, GroveErrorOwner.GFI, null, "File search", ErrorSeverity.DEGRADE,
        "Shared storage could not be searched completely.")
    val CONTACT_SEARCH = GroveError(610, GroveErrorOwner.GCI, null, "Contact search", ErrorSeverity.DEGRADE,
        "The contacts provider could not be read.")

    val all = listOf(
        CONFIG_IMPORT, CONFIG_EXPORT, CONFIG_PERSIST, CONFIG_LOAD,
        APP_CATALOG, LAUNCHER_SERVICE,
        WALLPAPER_PREVIEW, WALLPAPER_APPLY, WALLPAPER_SYNC, WALLPAPER_LOCAL_SYNC,
        TUTORIAL_REPLAY, REPORT_HANDOFF, NATIVE_BRIDGE, GENERIC_NONFATAL, UNCAUGHT_CRASH,
        FILE_SEARCH, CONTACT_SEARCH,
    )

    fun byCode(code: Int): GroveError? = all.firstOrNull { it.code == code }
}

internal data class GroveErrorRoute(val dismissLabel: String, val actionLabel: String?)

internal object GroveErrorRouting {
    fun route(error: GroveError, hasAction: Boolean): GroveErrorRoute = GroveErrorRoute(
        dismissLabel = when (error.severity) {
            ErrorSeverity.DEGRADE -> "Continue"
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
        val body = "${error.feature} · ${error.severity.label}\n${error.codeLine()}\n\n${error.summary}"
        val route = GroveErrorRouting.route(error, retry != null)
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle("Grove problem")
            .setMessage(body)
            .setNegativeButton(route.dismissLabel, null)
        if (retry != null) builder.setPositiveButton(route.actionLabel) { _, _ -> retry() }
        builder.setNeutralButton("Report") { _, _ ->
            if (CrashReporter.reportUserRequested(activity, error, null)) {
                activity.message("Problem report saved for your review")
                CrashReporter.reviewPending(activity)
            } else {
                activity.message("Could not save the problem report")
            }
        }
        builder.show()
    }
}

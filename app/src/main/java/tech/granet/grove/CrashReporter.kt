package tech.granet.grove

import android.app.Activity
import android.app.Application
import android.content.Context
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.message
import java.io.File

/**
 * Crash and error reporting with no third-party SDK and no network of its own.
 *
 * How it works:
 * - [install] hooks the default uncaught-exception handler. On a crash the report
 *   is written to the app's private files dir, then the previous handler runs so
 *   the system still kills/restarts the process normally.
 * - On the next launch [promptIfPending] asks the user whether to email the saved
 *   report(s) to the developer. Nothing is ever sent automatically.
 * - [reportNonFatal] lets caught code paths file an error report the same way.
 *
 * Reports never leave the device except through the email app the user picks.
 */
internal object ReportPromptPolicy {
    fun shouldPrompt(reportCount: Int, automaticCaptureEnabled: Boolean,
                     alreadyPrompting: Boolean, explicitReview: Boolean): Boolean =
        reportCount > 0 && !alreadyPrompting && (explicitReview || automaticCaptureEnabled)
}

internal object ReportHandoffPolicy {
    fun hasMailHandler(handlerCount: Int): Boolean = handlerCount > 0
}

object CrashReporter {
    private const val TAG = "Grove"
    private const val PREFS = "crash_reports"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMAIL = "developer_email"
    private const val DIR = "crash-reports"
    private const val MAX_REPORTS = 10
    private const val MAX_BODY_CHARS = 60_000
    private const val DEFAULT_EMAIL = "support@granet.tech"
    @Volatile private var prompting = false

    /** Install as early as possible (GroveApp.onCreate). Safe to call once. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabled(app)) writeReport(app, kind = "crash", error = GroveErrorRegistry.UNCAUGHT_CRASH, throwable)
            } catch (_: Exception) {
                // Never let the reporter break the crash path.
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean): Boolean =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()

    fun developerEmail(context: Context): String =
        prefs(context).getString(KEY_EMAIL, DEFAULT_EMAIL).orEmpty().ifBlank { DEFAULT_EMAIL }

    fun setDeveloperEmail(context: Context, email: String): Boolean =
        prefs(context).edit().putString(KEY_EMAIL, email.trim()).commit()

    /** File a report for a caught exception; surfaced at the next [promptIfPending]. */
    fun reportNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            if (isEnabled(context)) writeReport(context, kind = "error",
                error = GroveErrorRegistry.GENERIC_NONFATAL,
                throwable = throwable)
        } catch (_: Exception) {
        }
    }

    internal fun reportNonFatal(context: Context, error: GroveError, throwable: Throwable?) {
        try {
            if (isEnabled(context)) writeReport(context, kind = "error", error = error, throwable = throwable)
        } catch (_: Exception) {
        }
    }

    /** Explicit user report is allowed even when automatic crash/error capture is disabled. */
    internal fun reportUserRequested(context: Context, error: GroveError, throwable: Throwable?): Boolean = try {
        writeReport(context, kind = "user-report", error = error, throwable = throwable)
        true
    } catch (_: Exception) {
        false
    }

    /** Ask the user about automatically captured unsent reports on launch. */
    fun promptIfPending(activity: Activity) {
        prompt(activity, requireEnabled = true)
    }

    /** Review reports after an explicit user Report action, regardless of automatic capture preference. */
    internal fun reviewPending(activity: Activity) {
        prompt(activity, requireEnabled = false)
    }

    private fun prompt(activity: Activity, requireEnabled: Boolean) {
        val enabled = try { isEnabled(activity) } catch (_: Exception) { false }
        val reports = try { pendingReports(activity) } catch (_: Exception) { return }
        if (!ReportPromptPolicy.shouldPrompt(
                reportCount = reports.size,
                automaticCaptureEnabled = enabled,
                alreadyPrompting = prompting,
                explicitReview = !requireEnabled,
            )) return
        prompting = true
        val noun = if (reports.size == 1) "report" else "reports"
        activity.confirmDialog(
            title = "Grove ran into a problem",
            message = "${reports.size} $noun ${if (reports.size == 1) "was" else "were"} saved on this device. " +
                "Review an email draft to ${developerEmail(activity)}? Nothing is sent automatically.",
            positive = "Review email draft",
        ) {
            DiagnosticReportHandoff.sendReports(activity, reports)
        }.setOnDismissListener { prompting = false }
    }

    fun pendingCount(context: Context): Int = try {
        pendingReports(context).size
    } catch (_: Exception) {
        0
    }

    fun deleteAll(context: Context): Boolean = try {
        val reports = reportsDir(context).listFiles() ?: error("Cannot read reports")
        reports.fold(true) { removed, file -> (file.delete() || !file.exists()) && removed }
    } catch (_: Exception) {
        false
    }

    internal fun buildBody(context: Context, kind: String, error: GroveError, throwable: Throwable?) =
        DiagnosticReportFormatter.buildBody(context, kind, error, throwable)
    internal fun safeDiagnostic(throwable: Throwable?) = DiagnosticReportFormatter.safeDiagnostic(throwable)
    private fun pendingReports(context: Context) = DiagnosticReportStore.pendingReports(context)
    private fun writeReport(context: Context, kind: String, error: GroveError, throwable: Throwable?) =
        DiagnosticReportStore.writeReport(context, kind, error, throwable)
    private fun reportsDir(context: Context) = DiagnosticReportStore.reportsDir(context)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

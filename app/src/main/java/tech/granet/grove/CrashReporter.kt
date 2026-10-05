package tech.granet.grove

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.message
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun developerEmail(context: Context): String =
        prefs(context).getString(KEY_EMAIL, DEFAULT_EMAIL).orEmpty().ifBlank { DEFAULT_EMAIL }

    fun setDeveloperEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_EMAIL, email.trim()).apply()
    }

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
            sendReports(activity, reports)
        }.setOnDismissListener { prompting = false }
    }

    fun pendingCount(context: Context): Int = try {
        pendingReports(context).size
    } catch (_: Exception) {
        0
    }

    fun deleteAll(context: Context) {
        try {
            reportsDir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun sendReports(activity: Activity, reports: List<File>) {
        val body = try {
            reports.sortedBy { it.name }
                .joinToString("\n\n====================\n\n") { it.readText() }
                .take(MAX_BODY_CHARS)
        } catch (_: Exception) {
            activity.message("Could not read the saved reports")
            return
        }
        val email = developerEmail(activity)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Grove Launcher problem report")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            // A chooser launch is not proof of delivery. Reports remain until explicit discard.
            activity.startActivity(Intent.createChooser(intent, "Send problem report"))
        } catch (_: Exception) {
            val e = GroveErrorRegistry.REPORT_HANDOFF
            MaterialAlertDialogBuilder(activity)
                .setTitle("No email app found")
                .setMessage("Code ${e.code} · ${e.gws}\n\nNo mail app accepted the draft. You can copy the report and paste it into a message yourself. The saved report will remain in Grove until you explicitly delete it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Copy report") { _, _ ->
                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Grove problem report", body))
                    activity.message("Problem report copied")
                }
                .show()
        }
    }

    private fun pendingReports(context: Context): List<File> =
        reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }.orEmpty()

    private fun writeReport(context: Context, kind: String, error: GroveError, throwable: Throwable?) {
        val dir = reportsDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        File(dir, "$kind-$stamp.txt").writeText(buildBody(context, kind, error, throwable))
        // Keep only the newest reports.
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }?.dropLast(MAX_REPORTS)?.forEach { it.delete() }
    }

    internal fun buildBody(context: Context, kind: String, error: GroveError, throwable: Throwable?): String {
        val version = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.versionCode})"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
        return buildString {
            appendLine("Grove Launcher $kind report")
            appendLine("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("app: $version")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("feature: ${error.feature}")
            appendLine("severity: ${error.severity.label}")
            appendLine("code: ${error.code}")
            appendLine("gws: ${error.gws}")
            appendLine("summary: ${error.summary}")
            safeDiagnostic(throwable)?.let {
                appendLine("--- safe diagnostic ---")
                append(it)
            }
        }
    }

    internal fun safeDiagnostic(throwable: Throwable?): String? {
        if (throwable == null) return null
        return buildString {
            appendLine("exception: ${throwable.javaClass.name}")
            throwable.stackTrace.take(24).forEach { frame ->
                appendLine("at ${frame.className}.${frame.methodName}(${frame.fileName ?: "Unknown"}:${frame.lineNumber})")
            }
            appendLine("Exception messages, contact data, file paths, imported configuration, and sensitive values are intentionally omitted.")
        }
    }

    private fun reportsDir(context: Context): File = File(context.filesDir, DIR)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

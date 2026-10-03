package tech.granet.grove

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
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
object CrashReporter {
    private const val TAG = "Grove"
    private const val PREFS = "crash_reports"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMAIL = "developer_email"
    private const val DIR = "crash-reports"
    private const val MAX_REPORTS = 10
    private const val MAX_BODY_CHARS = 100_000

    /** Install as early as possible (GroveApp.onCreate). Safe to call once. */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabled(app)) writeReport(app, kind = "crash", tag = thread.name, throwable)
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
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    fun setDeveloperEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_EMAIL, email.trim()).apply()
    }

    /** File a report for a caught exception; surfaced at the next [promptIfPending]. */
    fun reportNonFatal(context: Context, tag: String, throwable: Throwable) {
        try {
            if (isEnabled(context)) writeReport(context, kind = "error", tag = tag, throwable)
        } catch (_: Exception) {
        }
    }

    /** Ask the user about unsent reports. Call from the main activity's onCreate. */
    fun promptIfPending(activity: Activity) {
        val reports = try {
            if (!isEnabled(activity)) return
            pendingReports(activity)
        } catch (_: Exception) {
            return
        }
        if (reports.isEmpty()) return
        val noun = if (reports.size == 1) "report" else "reports"
        activity.confirmDialog(
            title = "Grove ran into a problem",
            message = "${reports.size} $noun ${if (reports.size == 1) "was" else "were"} saved on this device. " +
                "Email ${if (reports.size == 1) "it" else "them"} to the developer? Nothing is sent automatically.",
            positive = "Send via email",
        ) {
            sendReports(activity, reports)
        }
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
            // An email chooser only confirms that an app opened, not that mail was sent.
            // Keep reports until the user explicitly deletes them in Grove settings.
            activity.startActivity(Intent.createChooser(intent, "Send problem report"))
        } catch (_: Exception) {
            activity.message("No email app found to send the report")
        }
    }

    private fun pendingReports(context: Context): List<File> =
        reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }.orEmpty()

    private fun writeReport(context: Context, kind: String, tag: String, throwable: Throwable) {
        val dir = reportsDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        File(dir, "$kind-$stamp.txt").writeText(buildBody(context, kind, tag, throwable))
        // Keep only the newest reports.
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }?.dropLast(MAX_REPORTS)?.forEach { it.delete() }
    }

    private fun buildBody(context: Context, kind: String, tag: String, throwable: Throwable): String {
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
            appendLine("tag: $tag")
            appendLine("---")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            append(sw.toString())
        }
    }

    private fun reportsDir(context: Context): File = File(context.filesDir, DIR)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

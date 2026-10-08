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

internal object DiagnosticReportHandoff {
    private const val MAX_BODY_CHARS = 60_000
    fun sendReports(activity: Activity, reports: List<File>) {
        val body = try {
            reports.sortedBy { it.name }
                .joinToString("\n\n====================\n\n") { file -> file.inputStream().use { input -> BoundedInput.read(input, 60_000).toString(Charsets.UTF_8) } }
                .take(MAX_BODY_CHARS)
        } catch (_: Exception) {
            activity.message("Could not read the saved reports")
            return
        }
        val email = CrashReporter.developerEmail(activity)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "Grove Launcher problem report")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val handlerCount = try {
            activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size
        } catch (_: Exception) {
            0
        }
        if (!ReportHandoffPolicy.hasMailHandler(handlerCount)) {
            showCopyFallback(activity, body)
            return
        }
        try {
            // A chooser launch is not proof of delivery. Reports remain until explicit discard.
            activity.startActivity(Intent.createChooser(intent, "Send problem report"))
        } catch (_: Exception) {
            showCopyFallback(activity, body)
        }
    }

    private fun showCopyFallback(activity: Activity, body: String) {
        val e = GroveErrorRegistry.REPORT_HANDOFF
        MaterialAlertDialogBuilder(activity)
            .setTitle("No email app found")
            .setMessage("${e.codeLine()}\n\nNo mail app accepted the draft. You can copy the report and paste it into a message yourself. The saved report will remain in Grove until you explicitly delete it.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Copy report") { _, _ ->
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                val copied = runCatching {
                    checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText("Grove problem report", body))
                }.isSuccess
                activity.message(if (copied) "Problem report copied" else "Could not copy report")
            }
            .show()
    }

}

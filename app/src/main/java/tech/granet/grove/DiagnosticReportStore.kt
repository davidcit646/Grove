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

/** Private bounded report persistence; handoff never deletes stored reports. */
internal object DiagnosticReportStore {
    private const val DIR = "crash-reports"
    private const val MAX_REPORTS = 10
    fun reportsDir(context: Context) = File(context.filesDir, DIR)
    fun pendingReports(context: Context): List<File> =
        reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }.orEmpty()

    fun writeReport(context: Context, kind: String, error: GroveError, throwable: Throwable?) {
        val dir = reportsDir(context).apply { mkdirs() }
        val stamp = System.currentTimeMillis().toString() + "-" + java.util.UUID.randomUUID()
        File(dir, "$kind-$stamp.txt").writeText(DiagnosticReportFormatter.buildBody(context, kind, error, throwable))
        // Keep only the newest reports.
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.lastModified() }?.dropLast(MAX_REPORTS)?.forEach { check(it.delete() || !it.exists()) { "Could not prune report" } }
    }

}

package tech.granet.grove

import android.content.Context
import java.io.File

/** Private bounded report persistence; handoff never deletes stored reports. */
internal object DiagnosticReportStore {
    private const val DIR = "crash-reports"
    private const val MAX_REPORTS = 10
    fun reportsDir(context: Context) = File(context.filesDir, DIR)
    fun pendingReports(context: Context): List<File> =
        reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name }.orEmpty()

    @Synchronized fun writeReport(context: Context, kind: String, error: GroveError, throwable: Throwable?) {
        val dir = reportsDir(context).apply { check(isDirectory || mkdirs()) { "Report storage unavailable" } }
        val stamp = System.currentTimeMillis().toString() + "-" + java.util.UUID.randomUUID()
        File(dir, "$kind-$stamp.txt").writeText(DiagnosticReportFormatter.buildBody(context, kind, error, throwable))
        // Keep only the newest reports.
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.lastModified() }?.dropLast(MAX_REPORTS)?.forEach { check(it.delete() || !it.exists()) { "Could not prune report" } }
    }

}

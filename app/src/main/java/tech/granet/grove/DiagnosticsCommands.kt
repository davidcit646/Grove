package tech.granet.grove

import android.content.Context

internal class DiagnosticsCommands(private val context: Context) {
    fun capture(enabled: Boolean) = checked { CrashReporter.setEnabled(context, enabled) }
    fun email(value: String) = checked { CrashReporter.setDeveloperEmail(context, value) }
    fun deleteReports() = checked { CrashReporter.deleteAll(context) }
}

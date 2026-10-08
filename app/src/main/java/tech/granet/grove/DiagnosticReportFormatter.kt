package tech.granet.grove

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object DiagnosticReportFormatter {
    internal fun buildBody(context: Context, kind: String, error: GroveError, throwable: Throwable?): String {
        val version = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
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
            error.gws?.let { appendLine("gws: $it") }
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

}

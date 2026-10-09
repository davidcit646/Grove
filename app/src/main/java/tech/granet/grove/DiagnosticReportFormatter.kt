package tech.granet.grove

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object DiagnosticReportFormatter {
    @Suppress("DEPRECATION")
    private fun legacyPackageInfo(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0)

    internal fun buildBody(context: Context, kind: String, error: GroveError, throwable: Throwable?): String {
        val version = try {
            val pi = if (Build.VERSION.SDK_INT >= 33)
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            else legacyPackageInfo(context)
            "${pi.versionName} (${pi.longVersionCode})"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val diagnostic = safeDiagnostic(throwable)
        val args = org.json.JSONObject().put("kind", kind).put("time", time).put("app", version)
            .put("device", device).put("android", android).put("feature", error.feature)
            .put("severity", error.severity.label).put("code", error.code.toString()).put("gws", error.gws ?: org.json.JSONObject.NULL)
            .put("summary", error.summary).put("diagnostic", diagnostic ?: org.json.JSONObject.NULL)
        (PortablePolicy.value("report", args) as? String)?.takeIf { it.length <= 60_000 }?.let { return it }
        return buildString {
            appendLine("Grove Launcher $kind report")
            appendLine("time: $time")
            appendLine("app: $version")
            appendLine("device: $device")
            appendLine("android: $android")
            appendLine("feature: ${error.feature}")
            appendLine("severity: ${error.severity.label}")
            appendLine("code: ${error.code}")
            error.gws?.let { appendLine("gws: $it") }
            appendLine("summary: ${error.summary}")
            diagnostic?.let {
                appendLine("--- safe diagnostic ---")
                append(it)
            }
        }
    }

    internal fun safeDiagnostic(throwable: Throwable?): String? {
        if (throwable == null) return null
        val frames = org.json.JSONArray().apply {
            throwable.stackTrace.take(24).forEach { frame -> put(org.json.JSONObject()
                .put("class", frame.className).put("method", frame.methodName)
                .put("file", frame.fileName ?: "Unknown").put("line", frame.lineNumber)) }
        }
        (PortablePolicy.value("diagnostic", org.json.JSONObject().put("type", throwable.javaClass.name).put("frames", frames)) as? String)
            ?.takeIf { it.length <= 60_000 }?.let { return it }
        return buildString {
            appendLine("exception: ${throwable.javaClass.name}")
            throwable.stackTrace.take(24).forEach { frame ->
                appendLine("at ${frame.className}.${frame.methodName}(${frame.fileName ?: "Unknown"}:${frame.lineNumber})")
            }
            appendLine("Exception messages, contact data, file paths, imported configuration, and sensitive values are intentionally omitted.")
        }
    }

}

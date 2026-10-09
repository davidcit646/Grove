package tech.granet.grove

import android.os.Trace

/** Optional system tracing: fixed operation names only, no query/contact/path content. */
internal object PerformanceTrace {
    fun <T> measure(name: String, operation: () -> T): T {
        val tracing = runCatching {
            if (Trace.isEnabled()) { Trace.beginSection(name); true } else false
        }.getOrDefault(false)
        try { return operation() }
        finally { if (tracing) runCatching { Trace.endSection() } }
    }
}

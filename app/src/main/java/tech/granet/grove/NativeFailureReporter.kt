package tech.granet.grove

/** Bounds diagnostic volume even when a failing JNI operation is called on every query. */
internal class NativeFailureReporter(private val report: (String, Throwable) -> Unit) {
    private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun failed(operation: String, error: Throwable) {
        val key = "$operation:${error.javaClass.name}"
        if (seen.add(key)) report(operation, error)
    }
}

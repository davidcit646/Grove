package tech.granet.grove

/** Stable score ordering shared by the dedicated search area and app drawer. */
internal object SearchResults {
    class Prepared<T> internal constructor(val items: List<T>, val labels: Array<String>, reuse: Boolean = true) {
        internal val buffer = if (reuse && CoreBridge.nativeAvailable) SearchBuffer.prepare(labels) else null
    }

    fun <T> prepare(items: List<T>, normalizedLabel: (T) -> String): Prepared<T> =
        PerformanceTrace.measure("Grove.search.prepare") { Prepared(items, items.map(normalizedLabel).toTypedArray()) }

    fun <T> matching(prepared: Prepared<T>, query: Search.Query, limit: Int = Int.MAX_VALUE): List<T> {
        if (query.text.isEmpty() || limit <= 0 || prepared.items.isEmpty()) return emptyList()
        return PerformanceTrace.measure("Grove.search.rank") {
            CoreBridge.searchOrder(prepared.labels, prepared.buffer, query, limit).map(prepared.items::get)
        }
    }

    fun <T> matching(items: List<T>, query: Search.Query, limit: Int = Int.MAX_VALUE,
                     normalizedLabel: (T) -> String): List<T> =
        matching(Prepared(items, items.map(normalizedLabel).toTypedArray(), reuse = false), query, limit)
}

package tech.granet.grove

/** Stable score ordering shared by the dedicated search area and app drawer. */
internal object SearchResults {
    fun <T> matching(items: List<T>, query: Search.Query, limit: Int = Int.MAX_VALUE,
                     normalizedLabel: (T) -> String): List<T> {
        if (query.text.isEmpty() || limit <= 0 || items.isEmpty()) return emptyList()
        // Scoring and top-K selection happen in one native call; the Kotlin
        // PriorityQueue path inside CoreBridge is the fallback when the
        // library is absent.
        return CoreBridge.searchOrder(items.map(normalizedLabel), query, limit).map(items::get)
    }
}

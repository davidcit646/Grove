package tech.granet.grove

/** Stable score ordering shared by the dedicated search area and app drawer. */
internal object SearchResults {
    class Prepared<T> internal constructor(val items: List<T>, val labels: Array<String>)

    fun <T> prepare(items: List<T>, normalizedLabel: (T) -> String): Prepared<T> =
        Prepared(items, items.map(normalizedLabel).toTypedArray())

    fun <T> matching(prepared: Prepared<T>, query: Search.Query, limit: Int = Int.MAX_VALUE): List<T> {
        if (query.text.isEmpty() || limit <= 0 || prepared.items.isEmpty()) return emptyList()
        return CoreBridge.searchOrder(prepared.labels, query, limit).map(prepared.items::get)
    }

    fun <T> matching(items: List<T>, query: Search.Query, limit: Int = Int.MAX_VALUE,
                     normalizedLabel: (T) -> String): List<T> =
        matching(prepare(items, normalizedLabel), query, limit)
}

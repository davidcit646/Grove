package tech.granet.grove

/** Pure helpers for deterministic pinned-app ordering. */
object PinnedApps {
    fun moveTo(items: List<String>, item: String, target: String): List<String> {
        if (item == target || item !in items || target !in items) return items
        val targetIndex = items.indexOf(target)
        val next = items.toMutableList()
        next.remove(item)
        if (targetIndex < 0) return items
        next.add(targetIndex, item)
        return next
    }

    fun shift(items: List<String>, item: String, delta: Int): List<String> {
        val from = items.indexOf(item)
        if (from < 0) return items
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (to == from) return items
        return items.toMutableList().also { next ->
            next.removeAt(from)
            next.add(to, item)
        }
    }
}

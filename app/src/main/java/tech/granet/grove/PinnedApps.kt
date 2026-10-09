package tech.granet.grove

/** Pure helpers for deterministic pinned-app ordering. */
object PinnedApps {
    fun moveTo(items: List<String>, item: String, target: String): List<String> {
        native(items, item, target, 0)?.let { return it }
        if (item == target || item !in items || target !in items) return items
        val targetIndex = items.indexOf(target)
        val next = items.toMutableList()
        next.remove(item)
        if (targetIndex < 0) return items
        next.add(targetIndex, item)
        return next
    }

    fun shift(items: List<String>, item: String, delta: Int): List<String> {
        native(items, item, null, delta)?.let { return it }
        val from = items.indexOf(item)
        if (from < 0) return items
        val to = (from.toLong() + delta).coerceIn(0, items.lastIndex.toLong()).toInt()
        if (to == from) return items
        return items.toMutableList().also { next ->
            next.removeAt(from)
            next.add(to, item)
        }
    }
    private fun native(items: List<String>, item: String, target: String?, delta: Int): List<String>? {
        val rows = PortablePolicy.value("pin", org.json.JSONObject().put("items", org.json.JSONArray(items))
            .put("item", item).put("target", target ?: org.json.JSONObject.NULL).put("delta", delta)) as? org.json.JSONArray ?: return null
        val moved = (0 until rows.length()).map { rows.getString(it) }
        return moved.takeIf { it.size == items.size && it.groupingBy { x -> x }.eachCount() == items.groupingBy { x -> x }.eachCount() }
    }
}

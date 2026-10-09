package tech.granet.grove

/** One Android confirmation at a time; cancellation stops the remaining batch. */
internal class UninstallBatch {
    private val pending = ArrayDeque<String>()
    private var current: String? = null

    private fun native(action: String, packages: List<String> = emptyList()): org.json.JSONObject? {
        val result = PortablePolicy.value("uninstall", org.json.JSONObject().put("action", action)
            .put("pending", org.json.JSONArray(pending.toList())).put("current", current ?: org.json.JSONObject.NULL)
            .put("packages", org.json.JSONArray(packages))) as? org.json.JSONObject ?: return null
        return try {
            val rows = result.getJSONArray("pending")
            val next = (0 until rows.length()).map(rows::getString)
            val selected = if (result.isNull("current")) null else result.getString("current")
            val known = (pending + listOfNotNull(current) + packages).toSet()
            require(next.all(known::contains) && (selected == null || selected in known))
            require(result.getInt("removed") >= 0)
            pending.clear(); pending.addAll(next); current = selected
            result
        } catch (_: Exception) { null }
    }

    fun start(packages: List<String>): String? {
        native("start", packages)?.let { return current }
        cancel()
        pending.addAll(packages.filter { it.isNotBlank() }.distinct())
        return advance()
    }

    fun accepted(): String? {
        native("accepted")?.let { return current }
        if (current == null) return null
        current = null
        return advance()
    }

    fun cancel(): Int {
        native("cancel")?.let { return it.getInt("removed") }
        val remaining = pending.size
        pending.clear()
        current = null
        return remaining
    }

    private fun advance(): String? = (if (pending.isEmpty()) null else pending.removeFirst())
        .also { current = it }
}

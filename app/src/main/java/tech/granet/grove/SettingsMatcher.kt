package tech.granet.grove

/** Small immutable catalogue, prepared once; never queries Android or protected sources. */
internal class SettingsMatcher(entries: List<SettingsEntry>) {
    private val prepared = entries.distinctBy { it.destination }.map { entry ->
        entry to (listOf(entry.title) + entry.aliases).map(::normalize)
    }
    fun matching(query: String, limit: Int = 6): List<SettingsEntry> {
        val preparedQuery = Search.prepare(normalize(query))
        if (preparedQuery.text.isEmpty() || limit <= 0) return emptyList()
        val candidates = if (preparedQuery.text in setOf("settings", "grove settings"))
            prepared.filter { it.first.id.startsWith("category-") } else prepared
        val args = org.json.JSONObject().put("labels", org.json.JSONArray().apply {
            candidates.forEach { (_, labels) -> put(org.json.JSONArray(labels)) }
        }).put("query", preparedQuery.text).put("limit", limit)
        val native = PortablePolicy.value("settingsRank", args) as? org.json.JSONArray
        if (native != null) {
            val indices = (0 until native.length()).map { native.getInt(it) }
            if (indices.size <= limit && indices.distinct().size == indices.size && indices.all { it in candidates.indices })
                return indices.map { candidates[it].first }
        }
        return candidates.map { (entry, labels) -> entry to labels.maxOf { Search.scoreNormalized(it, preparedQuery) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
    companion object {
        fun normalize(value: String) = Search.normalize(value).replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
    }
}

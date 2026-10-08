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
        return candidates.map { (entry, labels) -> entry to labels.maxOf { Search.scoreNormalized(it, preparedQuery) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
    companion object {
        fun normalize(value: String) = Search.normalize(value).replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
    }
}

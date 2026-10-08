package tech.granet.grove

/** Service/cache lifecycle names reserved by GROVE-STATUS; permission remains a separate gate. */
internal enum class IndexState(val label: String) {
    Indexed("Indexed"),
    NeedsIndexing("Needs indexing"),
    IndexingDisabled("Indexing disabled"),
    IndexingError("Indexing error"),
    IndexingReady("Indexing ready"),
    IndexingStale("Indexing stale"),
    CacheUnavailable("Cache unavailable"),
    CacheDisabled("Cache disabled");

    companion object {
        fun resolve(enabled: Boolean, permitted: Boolean, cacheExists: Boolean,
                    cacheReady: Boolean, working: Boolean, failed: Boolean, corrupt: Boolean = false,
                    partial: Boolean = false): IndexState {
            PortablePolicy.int("indexState", org.json.JSONObject().put("enabled", enabled).put("permitted", permitted)
                .put("exists", cacheExists).put("ready", cacheReady).put("working", working).put("failed", failed)
                .put("corrupt", corrupt).put("partial", partial), 0..7)?.let { return entries[it] }
            return when {
            !enabled && cacheExists -> IndexingDisabled // Deletion is pending.
            !enabled -> CacheDisabled
            !permitted || corrupt -> CacheUnavailable
            failed -> IndexingError
            cacheReady && partial -> IndexingStale
            cacheReady -> Indexed
            cacheExists -> IndexingStale
            working -> IndexingReady
            else -> NeedsIndexing
            }
        }
    }
}

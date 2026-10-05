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
                    cacheReady: Boolean, working: Boolean, failed: Boolean, corrupt: Boolean = false): IndexState = when {
            !enabled && cacheExists -> IndexingDisabled // Deletion is pending.
            !enabled -> CacheDisabled
            !permitted || corrupt -> CacheUnavailable
            failed -> IndexingError
            cacheReady -> Indexed
            cacheExists -> IndexingStale
            working -> IndexingReady
            else -> NeedsIndexing
        }
    }
}

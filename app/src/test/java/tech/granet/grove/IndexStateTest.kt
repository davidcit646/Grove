package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class IndexStateTest {
    @Test fun allContractStatesHaveDistinctInputs() {
        fun state(on: Boolean, grant: Boolean, exists: Boolean, ready: Boolean = false,
                  working: Boolean = false, failed: Boolean = false, corrupt: Boolean = false) =
            IndexState.resolve(on, grant, exists, ready, working, failed, corrupt)
        assertEquals(IndexState.Indexed, state(true, true, true, ready = true))
        assertEquals(IndexState.NeedsIndexing, state(true, true, false))
        assertEquals(IndexState.IndexingDisabled, state(false, true, true))
        assertEquals(IndexState.IndexingError, state(true, true, false, failed = true))
        assertEquals(IndexState.IndexingReady, state(true, true, false, working = true))
        assertEquals(IndexState.IndexingStale, state(true, true, true))
        assertEquals(IndexState.CacheUnavailable, state(true, false, false))
        assertEquals(IndexState.CacheUnavailable, state(true, true, true, corrupt = true))
        assertEquals(IndexState.CacheDisabled, state(false, true, false))
    }
}

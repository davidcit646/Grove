package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSourceStateTest {
    @Test fun permissionAndDisabledStatesOverrideCachedResults() {
        assertEquals(SearchSourceState.Disabled,
            SearchSourceState.resolve(false, true, false, false, 5))
        assertEquals(SearchSourceState.PermissionRequired,
            SearchSourceState.resolve(true, false, false, false, 5))
    }

    @Test fun emptySuccessIsDifferentFromFailureAndPartialScan() {
        assertEquals(SearchSourceState.Ready(0),
            SearchSourceState.resolve(true, true, false, false, 0))
        assertEquals(SearchSourceState.Failed,
            SearchSourceState.resolve(true, true, false, true, 0))
        assertEquals(SearchSourceState.Partial(4, 2),
            SearchSourceState.resolve(true, true, false, false, 4, 2))
        assertEquals(SearchSourceState.Loading,
            SearchSourceState.resolve(true, true, true, false, 4, 2))
    }

    @Test fun boundedFileScanMapsToPartialState() {
        val bounded = FileIndex.ScanResult(emptyList(), skippedDirectories = 0, truncated = true)
        assertEquals(SearchSourceState.Partial(12, 1), SearchSourceState.fromFileScan(bounded, 12))
    }

    @Test fun unreadableChildrenAndCompleteScansRemainDistinct() {
        val partial = FileIndex.ScanResult(emptyList(), skippedDirectories = 2, truncated = false)
        val complete = FileIndex.ScanResult(emptyList(), skippedDirectories = 0, truncated = false)
        assertEquals(SearchSourceState.Partial(4, 2), SearchSourceState.fromFileScan(partial, 4))
        assertEquals(SearchSourceState.Ready(0), SearchSourceState.fromFileScan(complete, 0))
    }
}

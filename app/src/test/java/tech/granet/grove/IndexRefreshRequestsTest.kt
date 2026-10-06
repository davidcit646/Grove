package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class IndexRefreshRequestsTest {
    @Test fun notificationBurstDoesNotReplaceQueuedOrRunningGeneration() {
        var active: String? = null
        var started: String? = null
        var pending: String? = null
        var scans = 0
        var markers = 0
        fun event() = IndexRefreshRequests.request(active, started, pending,
            schedule = { scans++; active = "scan-$scans"; true },
            defer = { markers++; pending = active; true })
        repeat(100) { assertTrue(event()) }
        assertEquals(1, scans); assertEquals(0, markers); assertEquals("scan-1", active)
        started = active
        repeat(100) { assertTrue(event()) }
        assertEquals(1, scans); assertEquals(1, markers); assertEquals("scan-1", active)
        // Completion consumes exactly one deferred request; its queued scan covers later events.
        val followUp = pending == active
        active = null; started = null; pending = null
        if (followUp) assertTrue(event())
        repeat(100) { assertTrue(event()) }
        assertEquals(2, scans); assertEquals(1, markers); assertEquals("scan-2", active)
    }

    @Test fun failedSchedulingOrDeferredMarkerNeverClaimsAcceptance() {
        var scheduled = false
        assertFalse(IndexRefreshRequests.request("current", "current", null,
            schedule = { scheduled = true; true }, defer = { false }))
        assertFalse(scheduled)
        assertFalse(IndexRefreshRequests.request(null, "old", "old",
            schedule = { false }, defer = { throw AssertionError("No active scan") }))
    }
}

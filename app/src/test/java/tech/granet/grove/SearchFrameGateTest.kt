package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SearchFrameGateTest {
    @Test fun duplicateSourceNotificationsPreserveCommittedRows() {
        val gate = SearchFrameGate()
        val target = Any()
        val rows = listOf("camera", SearchSourceState.Ready(1), listOf("camera/.Main"))
        assertTrue(gate.shouldRender(target, rows))
        repeat(20) { assertFalse(gate.shouldRender(target, rows.toList())) }
        assertTrue(gate.shouldRender(target, listOf("camera", SearchSourceState.PermissionRequired, emptyList<String>())))
    }
    @Test fun newQueryResultAndRecreatedTargetEachPublish() {
        val gate = SearchFrameGate()
        val target = Any()
        assertTrue(gate.shouldRender(target, listOf("ca", emptyList<String>())))
        assertTrue(gate.shouldRender(target, listOf("cam", emptyList<String>())))
        assertTrue(gate.shouldRender(target, listOf("cam", listOf("camera"))))
        assertTrue(gate.shouldRender(Any(), listOf("cam", listOf("camera"))))
    }
    @Test fun retentionDoesNotAuthorizeRevokedDisabledOrSupersededResults() {
        assertFalse(SearchPublicationGate.allowed(1, 2, true, true, true))
        assertFalse(SearchPublicationGate.allowed(2, 2, true, true, false))
        assertFalse(SearchPublicationGate.allowed(2, 2, true, false, true))
        assertFalse(SearchPublicationGate.allowed(2, 2, false, true, true))
    }
}

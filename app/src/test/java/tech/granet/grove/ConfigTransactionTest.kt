package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class ConfigTransactionTest {
    @Test fun persistenceFailureKeepsActiveStateAndStopsPublication() {
        var active = "previous"
        var failures = 0
        assertFalse(ConfigTransaction.commit("next", { throw IllegalStateException("disk") },
            { active = it }, { failures++ }))
        assertEquals("previous", active)
        assertEquals(1, failures)
    }
    @Test fun persistenceCompletesBeforePublication() {
        val events = mutableListOf<String>()
        assertTrue(ConfigTransaction.commit("next", { events += "persist:$it" },
            { events += "publish:$it" }, { fail("Unexpected failure") }))
        assertEquals(listOf("persist:next", "publish:next"), events)
    }
    @Test fun publicationInvariantIsNotReportedAsPersistenceFailure() {
        var failures = 0
        try {
            ConfigTransaction.commit("next", {}, { error("invariant") }, { failures++ })
            fail("Publication invariant must propagate")
        } catch (_: IllegalStateException) {
            assertEquals(0, failures)
        }
    }
}

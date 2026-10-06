package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SettingsScrollStateTest {
    @Test fun settingsRefreshAndBackKeepIndependentPagePositions() {
        val state = SettingsScrollState()
        state.remember("home", 950)
        assertEquals(950, state.position("home"))
        assertEquals(0, state.position("homeGrid"))
        state.remember("homeGrid", 120)
        assertEquals(950, state.position("home"))
        assertEquals(120, state.position("homeGrid"))
        state.remember("home", 1020)
        assertEquals(1020, state.position("home"))
    }
    @Test fun recreationRestoresKnownRoutesAndNormalizesInvalidPositions() {
        val state = SettingsScrollState()
        state.remember("home", 800)
        state.remember("help", 200)
        val restored = SettingsScrollState()
        restored.restore(state.snapshot() + mapOf("unknown" to 100, "search" to -20), setOf("home", "help", "search"))
        assertEquals(800, restored.position("home"))
        assertEquals(200, restored.position("help"))
        assertEquals(0, restored.position("search"))
        assertFalse(restored.snapshot().containsKey("unknown"))
        state.remember("home", 0)
        assertEquals(800, restored.position("home"))
    }
}

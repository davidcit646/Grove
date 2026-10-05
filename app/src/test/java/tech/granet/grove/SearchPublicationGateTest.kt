package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SearchPublicationGateTest {
    @Test fun currentAuthorizedResultCanPublish() {
        assertTrue(SearchPublicationGate.allowed(
            generation = 4,
            currentGeneration = 4,
            active = true,
            enabled = true,
            access = true,
        ))
    }

    @Test fun staleGenerationCannotPublish() {
        assertFalse(SearchPublicationGate.allowed(3, 4, true, true, true))
    }

    @Test fun revokedPermissionCannotPublish() {
        assertFalse(SearchPublicationGate.allowed(4, 4, true, true, false))
    }

    @Test fun disabledSourceCannotPublish() {
        assertFalse(SearchPublicationGate.allowed(4, 4, true, false, true))
    }

    @Test fun indexedCacheSupersedesLateLiveResult() {
        assertFalse(SearchPublicationGate.allowed(4, 4, true, true, true, cacheSupersedesLive = true))
    }

    @Test fun inactiveSearchCannotPublish() {
        assertFalse(SearchPublicationGate.allowed(4, 4, false, true, true))
    }
}

package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SearchTutorialSessionTest {
    @Test fun failedExplicitReplayStaysSuppressedUntilANewRequest() {
        val session = SearchTutorialSession()
        assertTrue(session.shouldPresent(SearchTutorialState.VERSION, true, 42L))
        var recovered = 0
        val fault = IllegalStateException("render failed")
        assertFalse(session.present({}, { throw fault }, {
            assertSame(fault, it)
            recovered++
        }))
        assertEquals(1, recovered)
        assertFalse(session.shouldPresent(SearchTutorialState.VERSION, true, 42L))
        assertFalse(session.shouldPresent(SearchTutorialState.VERSION, true, 42L))
        assertTrue(session.shouldPresent(SearchTutorialState.VERSION, true, 43L))
    }

    @Test fun homeSwipeIsSettledBeforeTheUnderlayIsHidden() {
        val session = SearchTutorialSession()
        var offset = 24f
        val events = mutableListOf<String>()
        assertTrue(session.present(
            { events += "settle"; offset = 0f },
            { assertEquals(0f, offset, 0f); events += "render" },
            { fail("Presentation should succeed") },
        ))
        // The existing underlay is already settled if the guide is dismissed incomplete.
        assertEquals(0f, offset, 0f)
        assertEquals(listOf("settle", "render"), events)
        assertEquals(0, session.state.page)
    }

    @Test fun partialPresentationFailureCanRestoreASettledUnderlay() {
        val session = SearchTutorialSession()
        var offset = 24f
        var visible = true
        assertFalse(session.present(
            { offset = 0f },
            { visible = false; throw IllegalStateException("partial render") },
            { visible = true },
        ))
        assertTrue(visible)
        assertEquals(0f, offset, 0f)
        assertTrue(session.state.sessionSuppressed)
        assertFalse(session.shouldPresent(0, false, 0L))
    }

    @Test fun preparationFailureNeverAttemptsRendering() {
        val session = SearchTutorialSession()
        var rendered = false
        var recovered = false
        assertFalse(session.present(
            { throw IllegalStateException("underlay unavailable") },
            { rendered = true },
            { recovered = true },
        ))
        assertFalse(rendered)
        assertTrue(recovered)
        assertFalse(session.shouldPresent(0, false, 0L))
    }

    @Test fun failedCompletionKeepsTheCapturedReplayRequestSuppressed() {
        val session = SearchTutorialSession()
        assertTrue(session.shouldPresent(0, true, 42L))
        session.state.forward(); session.state.forward()
        val durableVersion = 0
        assertFalse(session.state.completed { false })
        assertFalse(session.shouldPresent(durableVersion, true, 42L))
        assertTrue(session.shouldPresent(durableVersion, true, 43L))
    }

    @Test fun refreshFailureAndRecreationPreserveRequestScopedSuppression() {
        val session = SearchTutorialSession()
        assertTrue(session.shouldPresent(0, true, 42L))
        session.state.forward()
        session.suppress()
        val restored = SearchTutorialSession(
            SearchTutorialState(session.state.page, session.state.sessionSuppressed), session.request)
        assertEquals(1, restored.state.page)
        assertFalse(restored.shouldPresent(0, true, 42L))
        assertTrue(restored.shouldPresent(0, true, 43L))
    }

    @Test fun completedUsersDoNotPresentWithoutAnExplicitReplay() {
        val session = SearchTutorialSession()
        assertFalse(session.shouldPresent(SearchTutorialState.VERSION, false, 0L))
        assertTrue(session.shouldPresent(SearchTutorialState.VERSION, true, 42L))
    }
}

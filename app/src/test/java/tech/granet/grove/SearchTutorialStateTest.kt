package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SearchTutorialStateTest {
    @Test fun newAndExistingUnseenUsersEnterButCompletedUsersDoNot() {
        assertTrue(SearchTutorialState.shouldShow(0, false, false))
        assertFalse(SearchTutorialState.shouldShow(SearchTutorialState.VERSION, false, false))
        assertTrue(SearchTutorialState.shouldShow(SearchTutorialState.VERSION, true, false))
        assertFalse(SearchTutorialState.shouldShow(0, true, true))
    }
    @Test fun exactlyThreeForwardActionsFinishAndBackNeverCompletes() {
        val state = SearchTutorialState()
        assertFalse(state.back())
        assertFalse(state.forward()); assertEquals(1, state.page)
        assertFalse(state.forward()); assertEquals(2, state.page)
        assertTrue(state.forward()); assertEquals(2, state.page)
        assertTrue(state.back()); assertEquals(1, state.page)
    }
    @Test fun incompleteTutorialCannotPersistCompletion() {
        val state = SearchTutorialState()
        var writes = 0
        assertFalse(state.completed { writes++; true })
        assertFalse(state.sessionSuppressed)
        state.forward()
        assertFalse(state.completed { writes++; true })
        assertEquals(0, writes)
    }
    @Test fun failedCompletionDoesNotClaimSavedAndSuppressesRepeatedSessionPrompts() {
        val state = SearchTutorialState(2)
        var writes = 0
        assertFalse(state.completed { writes++; false })
        assertEquals(1, writes); assertTrue(state.sessionSuppressed)
        assertFalse(SearchTutorialState.shouldShow(0, false, state.sessionSuppressed))
        assertFalse(SearchTutorialState.shouldShow(0, true, state.sessionSuppressed))
    }
    @Test fun successfulCompletionAllowsLaterExplicitReplay() {
        val state = SearchTutorialState(2)
        assertTrue(state.completed { true })
        assertFalse(state.sessionSuppressed)
        assertTrue(SearchTutorialState.shouldShow(SearchTutorialState.VERSION, true, state.sessionSuppressed))
    }
    @Test fun restoredPageIsBoundedAndKeepsSessionSuppression() {
        assertEquals(0, SearchTutorialState(-1).page)
        assertEquals(2, SearchTutorialState(12).page)
        val state = SearchTutorialState(1, true)
        assertEquals(1, state.page); assertTrue(state.sessionSuppressed)
        assertTrue(state.back()); assertEquals(0, state.page)
    }
}

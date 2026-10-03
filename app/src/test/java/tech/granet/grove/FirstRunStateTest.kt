package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunStateTest {
    @Test fun disabledSwipesSkipPracticeAndBackReturnsToNavigation() {
        val state = FirstRunState(Config(), emptyList())
        assertFalse(state.back())
        assertNull(state.next(true, true))
        state.gestures = state.gestures.copy(swipeUpAppDrawer = false, swipeDownSearch = false)
        assertNull(state.next(true, true))
        assertEquals(3, state.page)
        assertTrue(state.back())
        assertEquals(1, state.page)
    }

    @Test fun finishCommitsOnlyAtEndAndDeniedPermissionsDisableSources() {
        val initial = Config(favorites = listOf("old/.Main"))
        val state = FirstRunState(initial, listOf("a/.Main", "b/.Main"))
        state.gestures = state.gestures.copy(swipeUpAppDrawer = false)
        state.togglePin("a/.Main", true)
        while (state.page < 6) assertNull(state.next(false, false))
        assertNull(state.next(false, false))
        val finished = state.next(false, false)!!
        assertFalse(finished.search.contacts)
        assertFalse(finished.search.files)
        assertEquals(listOf("a/.Main"), finished.favorites)
        assertEquals(Config().search, initial.search)
    }

    @Test fun pinLimitAndGrantedSourcesPreservedOnReplay() {
        val apps = (1..13).map { "app$it/.Main" }
        val state = FirstRunState(Config(search = SearchSettings(contacts = true, files = true)), apps)
        apps.take(12).forEach { assertTrue(state.togglePin(it, true)) }
        assertFalse(state.togglePin(apps.last(), true))
        while (state.page < 7) state.next(true, true)
        val finished = state.next(true, true)!!
        assertTrue(finished.search.contacts)
        assertTrue(finished.search.files)
        assertEquals(12, finished.favorites.size)
    }
}

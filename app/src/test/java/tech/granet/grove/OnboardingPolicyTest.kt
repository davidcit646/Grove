package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class OnboardingPolicyTest {
    @Test fun indexingDefaultsAreExclusiveToFreshInstalls() {
        val fresh = SetupDefaults.configuration(false, false, false)
        assertTrue(fresh.search.contactIndexing)
        assertTrue(fresh.search.fileIndexing)
        assertFalse(fresh.search.contacts)
        assertFalse(fresh.search.files)
        listOf(Triple(true, false, false), Triple(false, true, false),
            Triple(false, false, true)).forEach { (saved, initialized, completed) ->
            assertEquals(Config(), SetupDefaults.configuration(saved, initialized, completed))
        }
        // Recovery and old imports must not adopt the fresh-install policy.
        assertFalse(Config().search.contactIndexing)
        assertFalse(Config.parse("""{"version":7,"wallpaper":0,"favorites":[],"search":{"contacts":true}}""").search.contactIndexing)
    }

    @Test fun neitherPermissionNorIndexPreferenceAuthorizesADisabledSource() {
        for (search in listOf(false, true)) for (index in listOf(false, true)) for (access in listOf(false, true)) {
            val settings = SearchSettings(search, search, index, index)
            assertEquals(search && index && access, IndexAccessPolicy.contacts(settings, access))
            assertEquals(search && index && access, IndexAccessPolicy.files(settings, access))
        }
    }

    @Test fun denialDoesNotEraseIndexPreferenceAndReplayKeepsAnExplicitOptOut() {
        val fresh = FirstRunState(SetupDefaults.configuration(false, false, false), emptyList())
        fresh.search = fresh.search.copy(contacts = true, files = true)
        while (fresh.page < 7) fresh.next(false, false)
        val result = fresh.next(false, false)!!
        assertFalse(result.search.contacts); assertFalse(result.search.files)
        assertTrue(result.search.contactIndexing); assertTrue(result.search.fileIndexing)
        val replay = FirstRunState(result.copy(search = result.search.copy(contactIndexing = false)), emptyList())
        while (replay.page < 7) replay.next(true, true)
        assertFalse(replay.next(true, true)!!.search.contactIndexing)
    }

    @Test fun recreationPreservesProvisionalAnswersAndNormalizesAnInvalidPage() {
        val initial = Config(favorites = listOf("old/.Main"))
        val apps = listOf("old/.Main", "new/.Main")
        val before = FirstRunState(initial, apps)
        before.gestures = before.gestures.copy(swipeDownSearch = false, swipeUpAppDrawer = false)
        before.home = before.home.copy(showClock = false)
        before.search = SearchSettings(contacts = true, contactIndexing = true)
        before.togglePin("new/.Main", true)
        while (before.page < 6) before.next(true, true)
        val saved = Config.parse(before.snapshot().json())
        val after = FirstRunState(initial, apps)
        after.restore(saved, before.page, true, false, true)
        assertEquals(before.snapshot(), after.snapshot()); assertEquals(6, after.page)
        assertTrue(after.practicedUp); assertTrue(after.practicedHold)
        assertTrue(after.back()); assertEquals(5, after.page)
        assertTrue(initial.homeScreen.showClock)
        assertFalse(initial.search.contacts)
        assertEquals(listOf("old/.Main"), initial.favorites)
        after.restore(saved, 2, false, false, false)
        assertEquals(0, after.page) // Disabled swipe practice cannot be restored as an active page.
    }

    @Test fun backAndRtlReverseSlideDirection() {
        assertEquals(320f, FirstRunMotionPolicy.offset(320, true, false), 0f)
        assertEquals(-320f, FirstRunMotionPolicy.offset(320, false, false), 0f)
        assertEquals(-320f, FirstRunMotionPolicy.offset(320, true, true), 0f)
        assertEquals(320f, FirstRunMotionPolicy.offset(320, false, true), 0f)
    }
}

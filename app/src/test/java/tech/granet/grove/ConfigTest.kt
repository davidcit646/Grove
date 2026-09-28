package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class ConfigTest {
    @Test fun roundTripPreservesSettings() {
        val c = Config(
            favorites = listOf("example.app/.Main"),
            wallpaper = 2,
            gestures = GestureSettings(
                swipeDownSearch = false,
                swipeUpAppDrawer = true,
                tapHomeContextMenu = true,
                longPressHomeContextMenu = false,
            ),
            homeScreen = HomeScreenSettings(
                showAppsButton = false,
                showSearchButton = false,
                showClock = true,
                showPinnedApps = false,
                showPinnedAppsHint = false,
                pinnedAppsAtBottom = false,
                useWallpaperButtonColors = true,
            ),
            search = SearchSettings(contacts = true, files = false),
        )
        assertEquals(c, Config.parse(c.json()))
    }

    @Test fun versionOneMigratesWithVisibleDefaults() {
        val migrated = Config.parse("""{"version":1,"wallpaper":0,"favorites":[]}""")
        assertEquals(GestureSettings(), migrated.gestures)
        assertEquals(HomeScreenSettings(), migrated.homeScreen)
        assertTrue(migrated.homeScreen.showPinnedAppsHint)
        assertTrue(migrated.json().contains("\"version\": 7"))
        assertEquals(SearchSettings(contacts = true, files = true), migrated.search)
        assertFalse(migrated.homeScreen.useWallpaperButtonColors)
    }

    @Test fun versionTwoMigratesNewControls() {
        val migrated = Config.parse(
            """{"version":2,"wallpaper":0,"favorites":[],"gestures":{"swipeDownSearch":false,"swipeUpAppDrawer":true},"homeScreen":{"showAppsButton":false,"showClock":true,"showPinnedApps":false}}"""
        )
        assertFalse(migrated.gestures.swipeDownSearch)
        assertTrue(migrated.gestures.swipeUpAppDrawer)
        assertFalse(migrated.gestures.tapHomeContextMenu)
        assertTrue(migrated.gestures.longPressHomeContextMenu)
        assertFalse(migrated.homeScreen.showAppsButton)
        assertTrue(migrated.homeScreen.showSearchButton)
        assertTrue(migrated.homeScreen.showClock)
        assertFalse(migrated.homeScreen.showPinnedApps)
        assertTrue(migrated.homeScreen.pinnedAppsAtBottom)
    }

    @Test fun versionThreeKeepsOldPinnedPlacement() {
        val migrated = Config.parse(
            """{"version":3,"wallpaper":0,"favorites":[],"homeScreen":{"showAppsButton":true,"showSearchButton":true,"showClock":true,"showPinnedApps":true}}"""
        )
        assertTrue(migrated.homeScreen.pinnedAppsAtBottom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownVersionRejected() { Config.parse("""{"version":8,"wallpaper":0,"favorites":[]}""") }

    @Test fun disabledSearchSourcesSurviveExport() {
        val config = Config(search = SearchSettings(contacts = false, files = false))
        assertEquals(config.search, Config.parse(config.json()).search)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSearchSwitchRejected() {
        Config.parse("""{"version":7,"wallpaper":0,"favorites":[],"search":{"files":"true"}}""")
    }

    @Test fun foldersSurviveExportAndImport() {
        val config = Config(folders = listOf(AppFolder("Work", listOf("example.app/.Main"))))
        assertEquals(config, Config.parse(config.json()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun appCannotBelongToTwoFolders() {
        Config.parse("""{"version":5,"wallpaper":0,"favorites":[],"folders":[{"name":"One","apps":["example.app/.Main"]},{"name":"Two","apps":["example.app/.Main"]}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun badWallpaperRejected() { Config.parse("""{"version":4,"wallpaper":99,"favorites":[]}""") }

    @Test(expected = IllegalArgumentException::class)
    fun invalidComponentRejected() { Config.parse("""{"version":4,"wallpaper":0,"favorites":["bad"]}""") }

    @Test fun duplicatesRemoved() {
        assertEquals(1, Config.parse("""{"version":4,"wallpaper":0,"favorites":["a/.B","a/.B"]}""").favorites.size)
    }

    @Test fun everyHomeSettingCombinationRoundTrips() {
        for (mask in 0 until 128) {
            val home = HomeScreenSettings(
                showAppsButton = mask and 1 != 0,
                showSearchButton = mask and 2 != 0,
                showClock = mask and 4 != 0,
                showPinnedApps = mask and 8 != 0,
                pinnedAppsAtBottom = mask and 16 != 0,
                showPinnedAppsHint = mask and 32 != 0,
                useWallpaperButtonColors = mask and 64 != 0,
            )
            val parsed = Config.parse(Config(homeScreen = home).json())
            assertEquals(home, parsed.homeScreen)
        }
    }

    @Test fun everyGestureCombinationRoundTrips() {
        for (mask in 0 until 16) {
            val gestures = GestureSettings(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0)
            assertEquals(gestures, Config.parse(Config(gestures = gestures).json()).gestures)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedToggleRejected() {
        Config.parse("""{"version":4,"wallpaper":0,"favorites":[],"gestures":{"swipeDownSearch":"false"}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedSectionRejected() {
        Config.parse("""{"version":4,"wallpaper":0,"favorites":[],"homeScreen":false}""")
    }
}

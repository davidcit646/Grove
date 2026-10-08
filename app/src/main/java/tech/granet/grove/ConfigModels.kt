package tech.granet.grove

import org.json.JSONArray
import org.json.JSONObject

/** Gesture behavior for the launcher home screen. */
data class GestureSettings(
    val swipeDownSearch: Boolean = true,
    val swipeUpAppDrawer: Boolean = true,
    val tapHomeContextMenu: Boolean = false,
    val longPressHomeContextMenu: Boolean = true,
)

/** Visibility and placement of optional home-screen elements. */
data class HomeScreenSettings(
    val showAppsButton: Boolean = true,
    val showSearchButton: Boolean = true,
    val showClock: Boolean = true,
    val tapClockOpensClock: Boolean = true,
    val showPinnedApps: Boolean = true,
    val useWallpaperButtonColors: Boolean = false,
    // True preserves the original Grove layout: pins sit after widgets and above All apps.
    // False moves pins directly below the clock/search controls.
    val pinnedAppsAtBottom: Boolean = true,
)

data class AppFolder(val name: String, val apps: List<String>)

/** Search sources can be disabled without revoking Android permissions. */
data class SearchSettings(
    val contacts: Boolean = false,
    val files: Boolean = false,
    val contactIndexing: Boolean = false,
    val fileIndexing: Boolean = false,
    val calculator: Boolean = true,
    val androidSettings: Boolean = true,
    val groveSettings: Boolean = true,
)

/** Persistent user settings. Widget IDs are device-local and deliberately excluded from exports. */
data class Config(
    val favorites: List<String> = emptyList(),
    val wallpaper: Int = 0,
    val gestures: GestureSettings = GestureSettings(),
    val homeScreen: HomeScreenSettings = HomeScreenSettings(),
    val folders: List<AppFolder> = emptyList(),
    val search: SearchSettings = SearchSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val homeGrid: IconGrid? = null,
    val drawerGrid: IconGrid? = null,
) {
    fun json(): String = ConfigCodec.encode(this)
    companion object { fun parse(text: String): Config = ConfigCodec.parse(text) }
}

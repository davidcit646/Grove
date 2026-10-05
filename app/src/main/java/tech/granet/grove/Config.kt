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
    val showPinnedAppsHint: Boolean = true,
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
)

/** Persistent user settings. Widget IDs are device-local and deliberately excluded from exports. */
data class Config(
    val favorites: List<String> = emptyList(),
    val wallpaper: Int = 0,
    val gestures: GestureSettings = GestureSettings(),
    val homeScreen: HomeScreenSettings = HomeScreenSettings(),
    val folders: List<AppFolder> = emptyList(),
    val search: SearchSettings = SearchSettings(),
) {
    fun json(): String = JSONObject()
        .put("version", 9)
        .put("wallpaper", WallpaperArt.source(wallpaper)?.id ?: error("Wallpaper selection is invalid"))
        .put("favorites", JSONArray(favorites))
        .put("folders", JSONArray().apply { folders.forEach { folder ->
            put(JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps)))
        } })
        .put("search", JSONObject().put("contacts", search.contacts).put("files", search.files)
            .put("contactIndexing", search.contactIndexing).put("fileIndexing", search.fileIndexing))
        .put(
            "gestures",
            JSONObject()
                .put("swipeDownSearch", gestures.swipeDownSearch)
                .put("swipeUpAppDrawer", gestures.swipeUpAppDrawer)
                .put("tapHomeContextMenu", gestures.tapHomeContextMenu)
                .put("longPressHomeContextMenu", gestures.longPressHomeContextMenu),
        )
        .put(
            "homeScreen",
            JSONObject()
                .put("showAppsButton", homeScreen.showAppsButton)
                .put("showSearchButton", homeScreen.showSearchButton)
                .put("showClock", homeScreen.showClock)
                .put("tapClockOpensClock", homeScreen.tapClockOpensClock)
                .put("showPinnedApps", homeScreen.showPinnedApps)
                .put("showPinnedAppsHint", homeScreen.showPinnedAppsHint)
                .put("useWallpaperButtonColors", homeScreen.useWallpaperButtonColors)
                .put("pinnedAppsAtBottom", homeScreen.pinnedAppsAtBottom),
        )
        .toString(2)

    companion object {
        fun parse(text: String): Config {
            val root = JSONObject(text)
            val version = root.getInt("version")
            require(version in 1..9) { "Unsupported configuration version" }

            val wallpaper = if (version >= 9) {
                val id = root.getString("wallpaper")
                WallpaperArt.indexForId(id) ?: throw IllegalArgumentException("Wallpaper selection is invalid")
            } else {
                root.getInt("wallpaper").also {
                    require(WallpaperArt.source(it) != null) { "Wallpaper selection is invalid" }
                }
            }

            val entries = root.getJSONArray("favorites")
            require(entries.length() <= 100) { "Too many favorites" }
            val favorites = (0 until entries.length()).map { entries.getString(it) }
            require(favorites.all { it.length in 3..512 && it.contains('/') }) { "Invalid app identifier" }

            // Older versions had fewer gesture/home controls. Defaults deliberately preserve
            // their existing layout and behavior when imported into the current schema.
            fun section(name: String): JSONObject? {
                if (!root.has(name)) return null
                require(root.get(name) is JSONObject) { "$name must be an object" }
                return root.getJSONObject(name)
            }
            fun flag(section: JSONObject?, name: String, default: Boolean): Boolean {
                if (section == null || !section.has(name)) return default
                val value = section.get(name)
                require(value is Boolean) { "$name must be true or false" }
                return value
            }
            val gestureJson = section("gestures")
            val gestures = GestureSettings(
                swipeDownSearch = flag(gestureJson, "swipeDownSearch", true),
                swipeUpAppDrawer = flag(gestureJson, "swipeUpAppDrawer", true),
                tapHomeContextMenu = flag(gestureJson, "tapHomeContextMenu", false),
                longPressHomeContextMenu = flag(gestureJson, "longPressHomeContextMenu", true),
            )

            val homeJson = section("homeScreen")
            val homeScreen = HomeScreenSettings(
                showAppsButton = flag(homeJson, "showAppsButton", true),
                showSearchButton = flag(homeJson, "showSearchButton", true),
                showClock = flag(homeJson, "showClock", true),
                tapClockOpensClock = flag(homeJson, "tapClockOpensClock", true),
                showPinnedApps = flag(homeJson, "showPinnedApps", true),
                showPinnedAppsHint = flag(homeJson, "showPinnedAppsHint", true),
                useWallpaperButtonColors = flag(homeJson, "useWallpaperButtonColors", false),
                pinnedAppsAtBottom = flag(homeJson, "pinnedAppsAtBottom", true),
            )

            // Old exported configurations retain the search behavior they had before switches.
            val searchJson = section("search")
            val search = SearchSettings(
                contacts = flag(searchJson, "contacts", version < 7),
                files = flag(searchJson, "files", version < 7),
                // Existing users had only search switches. Never silently opt them into durable storage.
                contactIndexing = flag(searchJson, "contactIndexing", false),
                fileIndexing = flag(searchJson, "fileIndexing", false),
            )

            val folders = if (root.has("folders")) {
                val array = root.getJSONArray("folders")
                require(array.length() <= 100) { "Too many folders" }
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val name = item.getString("name").trim()
                    require(name.length in 1..40) { "Invalid folder name" }
                    val members = item.getJSONArray("apps")
                    require(members.length() <= 500) { "Too many folder apps" }
                    AppFolder(name, (0 until members.length()).map { members.getString(it) }.also { keys ->
                        require(keys.all { it.length in 3..512 && it.contains('/') }) { "Invalid folder app" }
                    }.distinct())
                }.also { list ->
                    require(list.map { it.name.lowercase() }.distinct().size == list.size) { "Duplicate folder name" }
                    require(list.flatMap { it.apps }.distinct().size == list.sumOf { it.apps.size }) { "App in multiple folders" }
                }
            } else emptyList()
            return Config(favorites.distinct(), wallpaper, gestures, homeScreen, folders, search)
        }
    }
}

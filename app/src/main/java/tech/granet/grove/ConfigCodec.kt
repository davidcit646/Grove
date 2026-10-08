package tech.granet.grove

import org.json.JSONArray
import org.json.JSONObject

/** Portable codec adapter. Native schema validation and recovery decoding have explicit boundaries. */
internal object ConfigCodec {
    fun encode(config: Config): String = with(config) { JSONObject()
        .put("version", 12)
        .put("themeMode", themeMode.id)
        .put("homeGrid", homeGrid?.let { JSONObject().put("columns", it.columns).put("rows", it.rows) } ?: JSONObject.NULL)
        .put("drawerGrid", drawerGrid?.let { JSONObject().put("columns", it.columns).put("rows", it.rows) } ?: JSONObject.NULL)
        .put("wallpaper", WallpaperArt.source(wallpaper)?.id ?: error("Wallpaper selection is invalid"))
        .put("favorites", JSONArray(favorites))
        .put("folders", JSONArray().apply { folders.forEach { folder ->
            put(JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps)))
        } })
        .put("search", JSONObject().put("contacts", search.contacts).put("files", search.files)
            .put("contactIndexing", search.contactIndexing).put("fileIndexing", search.fileIndexing)
            .put("calculator", search.calculator).put("androidSettings", search.androidSettings)
            .put("groveSettings", search.groveSettings))
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
                .put("useWallpaperButtonColors", homeScreen.useWallpaperButtonColors)
                .put("pinnedAppsAtBottom", homeScreen.pinnedAppsAtBottom),
        )
        .toString(2)

    }
    fun parse(text: String): Config {
            ConfigInput.validate(text)
            val response = CoreBridge.portable("config", JSONObject().put("text", text))
            if (response?.has("error") == true) throw IllegalArgumentException(response.getString("error"))
            // Recovery decoder remains available when the native library cannot load.
            return decode(response?.getJSONObject("value") ?: JSONObject(text))
    }
    internal fun recovery(text: String): Config { ConfigInput.validate(text); return decode(JSONObject(text)) }
    private fun decode(root: JSONObject): Config {
            val versionValue = root.get("version")
            require(versionValue is Int || versionValue is Long) { "Configuration version must be an integer" }
            val version = (versionValue as Number).toLong().also { require(it in 1..12) }.toInt()
            require(version in 1..12) { "Unsupported configuration version" }

            val wallpaper = if (version >= 9) {
                val value = root.get("wallpaper")
                require(value is String) { "Wallpaper selection is invalid" }
                WallpaperArt.indexForId(value) ?: throw IllegalArgumentException("Wallpaper selection is invalid")
            } else {
                root.get("wallpaper").let { value ->
                    require(value is Int || value is Long) { "Wallpaper selection must be an integer" }
                    val number = (value as Number).toLong()
                    require(number in 0..Int.MAX_VALUE)
                    number.toInt()
                }.also {
                    require(WallpaperArt.source(it) != null) { "Wallpaper selection is invalid" }
                }
            }

            val entries = root.getJSONArray("favorites")
            require(entries.length() <= 100) { "Too many favorites" }
            val favorites = (0 until entries.length()).map { (entries.get(it) as? String ?: throw IllegalArgumentException("Invalid app identifier")) }
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
                calculator = flag(searchJson, "calculator", true),
                androidSettings = flag(searchJson, "androidSettings", true),
                groveSettings = flag(searchJson, "groveSettings", true),
            )

            val folders = if (root.has("folders")) {
                val array = root.getJSONArray("folders")
                require(array.length() <= 100) { "Too many folders" }
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    val name = (item.get("name") as? String ?: throw IllegalArgumentException("Invalid folder name")).trim()
                    require(name.length in 1..40) { "Invalid folder name" }
                    val members = item.getJSONArray("apps")
                    require(members.length() <= 500) { "Too many folder apps" }
                    AppFolder(name, (0 until members.length()).map { (members.get(it) as? String ?: throw IllegalArgumentException("Invalid folder app")) }.also { keys ->
                        require(keys.all { it.length in 3..512 && it.contains('/') }) { "Invalid folder app" }
                    }.distinct())
                }.also { list ->
                    require(list.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == list.size) { "Duplicate folder name" }
                    require(list.flatMap { it.apps }.distinct().size == list.sumOf { it.apps.size }) { "App in multiple folders" }
                }
            } else emptyList()
            val themeMode = if (version >= 10) {
                val value = root.get("themeMode")
                require(value is String) { "Theme mode must be a string" }
                ThemeMode.parse(value)
            } else ThemeMode.SYSTEM
            fun grid(name: String): IconGrid? {
                if (version < 11 || !root.has(name) || root.isNull(name)) return null
                val value = root.get(name)
                require(value is JSONObject) { "$name must be an object" }
                fun dimension(key: String): Int {
                    val number = value.get(key)
                    require(number is Int || number is Long) { "$name $key must be an integer" }
                    val long = (number as Number).toLong()
                    require(long in 1..10) { "$name $key must be 1–10" }
                    return long.toInt()
                }
                return IconGrid(dimension("columns"), dimension("rows"))
            }
            return Config(favorites.distinct(), wallpaper, gestures, homeScreen, folders, search, themeMode,
                grid("homeGrid"), grid("drawerGrid"))
        }
}

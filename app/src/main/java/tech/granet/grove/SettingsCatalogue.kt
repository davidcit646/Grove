package tech.granet.grove

/** Stable discovery metadata; destinations navigate only, never own or write preferences. */
internal enum class SettingsIcon { SETTINGS, HOME, GRID, FOLDER, WIDGET, PALETTE, IMAGE, DOCUMENT, HELP, MAIL, INFO, SHIELD, WIFI, BLUETOOTH, AIRPLANE, SOUND, BATTERY, LOCATION }
internal sealed class SettingsDestination {
    data class Grove(val route: String, val anchor: String? = null) : SettingsDestination()
    data class Android(val action: String) : SettingsDestination()
}
internal data class SettingsEntry(val id: String, val title: String, val breadcrumb: String,
    val icon: SettingsIcon, val destination: SettingsDestination, val aliases: List<String> = emptyList(),
    val key: SettingKey? = null)

internal object SettingsCatalogue {
    private fun grove(id: String, title: String, route: String, breadcrumb: String,
                      aliases: String, icon: SettingsIcon = SettingsIcon.SETTINGS, key: SettingKey? = null,
                      anchor: String? = id) = SettingsEntry(id, title, breadcrumb, icon,
        SettingsDestination.Grove(route, anchor), listOf(aliases), key)
    val grove = listOf(
        grove("APPS_BUTTON", "Show Apps button", "home", "Home screen > Buttons", "apps button", SettingsIcon.GRID, SettingKey.APPS_BUTTON),
        grove("SEARCH_BUTTON", "Show Search button", "home", "Home screen > Buttons", "search button", SettingsIcon.SETTINGS, SettingKey.SEARCH_BUTTON),
        grove("CLOCK", "Show clock and date", "home", "Home screen > Clock and date", "time date clock", SettingsIcon.SETTINGS, SettingKey.CLOCK),
        grove("CLOCK_ACTION", "Tap clock to open Clock", "home", "Home screen > Clock and date", "clock tap", SettingsIcon.SETTINGS, SettingKey.CLOCK_ACTION),
        grove("PINS", "Show pinned apps", "home", "Home screen > Pinned apps", "favorites pins", SettingsIcon.SETTINGS, SettingKey.PINS),
        grove("PIN_HINT", "Show pinned apps hint", "home", "Home screen > Pinned apps", "hint favorites", SettingsIcon.SETTINGS, SettingKey.PIN_HINT),
        grove("PIN_BOTTOM", "Pinned apps at bottom", "home", "Home screen > Pinned apps", "position favorites", SettingsIcon.SETTINGS, SettingKey.PIN_BOTTOM),
        grove("SWIPE_SEARCH", "Swipe down to search", "home", "Home screen > Gestures", "gestures swipe search", SettingsIcon.SETTINGS, SettingKey.SWIPE_SEARCH),
        grove("SWIPE_DRAWER", "Swipe up for app drawer", "home", "Home screen > Gestures", "gestures swipe drawer", SettingsIcon.SETTINGS, SettingKey.SWIPE_DRAWER),
        grove("TAP_MENU", "Tap empty space for settings", "home", "Home screen > Gestures", "gestures tap settings", SettingsIcon.SETTINGS, SettingKey.TAP_MENU),
        grove("HOLD_MENU", "Hold empty space for settings", "home", "Home screen > Gestures", "gestures long press settings", SettingsIcon.SETTINGS, SettingKey.HOLD_MENU),
        grove("WALLPAPER_COLORS", "Wallpaper colors for buttons", "appearance", "Appearance > Theme and colors", "material you palette colors", SettingsIcon.PALETTE, SettingKey.WALLPAPER_COLORS),
        grove("CALCULATOR", "Calculator", "search", "Search > Search features", "math arithmetic calculate answer", SettingsIcon.SETTINGS, SettingKey.CALCULATOR),
        grove("GROVE_SETTINGS", "Grove settings search", "search", "Search > Search features", "grove launcher settings search", SettingsIcon.SETTINGS, SettingKey.GROVE_SETTINGS),
        grove("ANDROID_SETTINGS", "Android settings search", "search", "Search > Search features", "android system settings wifi bluetooth search", SettingsIcon.SETTINGS, SettingKey.ANDROID_SETTINGS),
        grove("CONTACTS", "Search contacts", "search", "Search > Contacts", "people contact search", SettingsIcon.SETTINGS, SettingKey.CONTACTS),
        grove("FILES", "Search files", "search", "Search > Files", "documents file search", SettingsIcon.SETTINGS, SettingKey.FILES),
        grove("CONTACT_INDEX", "Background contact indexing", "search", "Search > Contacts", "contact index refresh cache background", SettingsIcon.SETTINGS, SettingKey.CONTACT_INDEX),
        grove("FILE_INDEX", "Background file indexing", "search", "Search > Files", "file index refresh cache background", SettingsIcon.SETTINGS, SettingKey.FILE_INDEX),
        grove("widgets", "Add widget", "home", "Home screen > Widgets", "widgets add resize", SettingsIcon.WIDGET, anchor = "widgets"),
        grove("homeGrid", "Home icon grid", "homeGrid", "Home screen > Pinned apps", "grid columns rows layout icons", SettingsIcon.GRID, anchor = null),
        grove("drawerGrid", "App drawer icon grid", "drawerGrid", "App drawer > Layout", "grid columns rows layout icons", SettingsIcon.GRID, anchor = null),
        grove("folders", "Manage folders and apps", "drawer", "App drawer > Folders and apps", "folders organize selection", SettingsIcon.FOLDER, anchor = "folders"),
        grove("launcher", "Choose launcher", "home", "Home screen > Default launcher", "default home launcher", SettingsIcon.HOME, anchor = "launcher"),
        grove("theme", "Theme", "theme", "Appearance > Theme and colors", "dark mode light mode system material you palette", SettingsIcon.PALETTE, anchor = null),
        grove("wallpaper", "Choose wallpaper", "appearance", "Appearance > Wallpaper", "background image black wallpaper", SettingsIcon.IMAGE, anchor = "wallpaper"),
        grove("import", "Import configuration", "configuration", "Configuration > Import and export", "import backup restore", SettingsIcon.DOCUMENT, anchor = "import"),
        grove("export", "Export configuration", "configuration", "Configuration > Import and export", "export backup save", SettingsIcon.DOCUMENT, anchor = "export"),
        grove("editor", "Advanced editor", "editor", "Configuration > Advanced", "json configuration editor", SettingsIcon.DOCUMENT, anchor = null),
        grove("recovery", "Recovery and defaults", "configuration", "Configuration > Recovery and defaults", "recover damaged configuration restore defaults", SettingsIcon.DOCUMENT, anchor = "recovery"),
        grove("defaults", "Restore defaults", "defaults", "Configuration > Recovery and defaults", "reset factory defaults", SettingsIcon.DOCUMENT, anchor = null),
        grove("tutorial", "Replay first-run setup", "help", "Help > Tutorials", "tutorial onboarding replay", SettingsIcon.HELP, anchor = "tutorial"),
        grove("searchTutorial", "Replay search tutorial", "help", "Help > Tutorials", "search tutorial guide features replay", SettingsIcon.HELP),
        grove("capture", "Automatic crash reports", "help", "Help > Local reports", "crash diagnostic logging reports", SettingsIcon.HELP, anchor = "capture"),
        grove("email", "Developer email", "email", "Help > Local reports", "support developer email", SettingsIcon.MAIL, anchor = null),
        grove("reports", "Review saved reports", "help", "Help > Local reports", "errors crash diagnostic reports", SettingsIcon.DOCUMENT, anchor = "reports"),
        grove("deleteReports", "Delete saved reports", "deleteReports", "Help > Local reports", "delete clear crash reports", SettingsIcon.DOCUMENT, anchor = null),
        grove("project", "Project", "about", "About > Project and information", "source github issues open source", SettingsIcon.INFO, anchor = "project"),
        grove("privacy", "Privacy", "about", "About > Project and information", "privacy data telemetry", SettingsIcon.SHIELD, anchor = "privacy"),
        grove("licenses", "Licenses", "about", "About > Project and information", "license attribution apache", SettingsIcon.DOCUMENT, anchor = "licenses"),
        grove("credits", "Artwork credits", "credits", "About > Project and information", "artwork credits license wallpaper", SettingsIcon.IMAGE, anchor = null),
        grove("category-root", "Launcher settings", "root", "Grove", "settings Launcher settings", anchor = null),
        grove("category-home", "Home screen settings", "home", "Grove", "settings Home screen settings", anchor = null),
        grove("category-drawer", "App drawer settings", "drawer", "Grove", "settings App drawer settings", anchor = null),
        grove("category-search", "Search settings", "search", "Grove", "settings Search settings", anchor = null),
        grove("category-appearance", "Appearance settings", "appearance", "Grove", "settings Appearance settings", anchor = null),
        grove("category-configuration", "Configuration settings", "configuration", "Grove", "settings Configuration settings", anchor = null),
        grove("category-help", "Help and diagnostics", "help", "Grove", "settings Help and diagnostics", anchor = null),
        grove("category-about", "About Grove", "about", "Grove", "settings About Grove", anchor = null),
        grove("access-contacts", "Contact access", "search", "Search > Contacts", "contact permission allow access", SettingsIcon.SHIELD),
        grove("access-files", "File access", "search", "Search > Files", "file storage permission allow access", SettingsIcon.SHIELD),
        grove("refresh-contacts", "Refresh contact index", "search", "Search > Contacts", "refresh contact index rebuild cache", SettingsIcon.SETTINGS),
        grove("refresh-files", "Refresh file index", "search", "Search > Files", "refresh file index rebuild cache", SettingsIcon.SETTINGS),
    )
    private fun android(id: String, title: String, suffix: String, icon: SettingsIcon, vararg aliases: String) =
        SettingsEntry("android-$id", title, "Android settings", icon,
            SettingsDestination.Android("android.settings.$suffix"), aliases.toList())
    val android = listOf(
        android("wifi", "Wi-Fi", "WIFI_SETTINGS", SettingsIcon.WIFI, "wifi", "wireless internet network"),
        android("bluetooth", "Bluetooth", "BLUETOOTH_SETTINGS", SettingsIcon.BLUETOOTH, "bluetooth pairing headphones"),
        android("airplane", "Airplane mode", "AIRPLANE_MODE_SETTINGS", SettingsIcon.AIRPLANE, "flight mode airplane"),
        android("wireless", "Network and internet", "WIRELESS_SETTINGS", SettingsIcon.WIFI, "mobile data hotspot connections"),
        android("display", "Display", "DISPLAY_SETTINGS", SettingsIcon.IMAGE, "brightness screen timeout rotation"),
        android("sound", "Sound", "SOUND_SETTINGS", SettingsIcon.SOUND, "volume ringtone vibration audio"),
        android("notifications", "Notifications", "NOTIFICATION_SETTINGS", SettingsIcon.INFO, "notifications alerts"),
        android("apps", "Apps", "APPLICATION_SETTINGS", SettingsIcon.GRID, "applications installed apps"),
        android("storage", "Storage", "INTERNAL_STORAGE_SETTINGS", SettingsIcon.DOCUMENT, "storage space disk"),
        android("battery", "Battery saver", "BATTERY_SAVER_SETTINGS", SettingsIcon.BATTERY, "battery power energy"),
        android("security", "Security", "SECURITY_SETTINGS", SettingsIcon.SHIELD, "security lock password"),
        android("privacy", "Privacy", "PRIVACY_SETTINGS", SettingsIcon.SHIELD, "privacy permissions"),
        android("accessibility", "Accessibility", "ACCESSIBILITY_SETTINGS", SettingsIcon.HELP, "accessibility text size talkback"),
        android("location", "Location", "LOCATION_SOURCE_SETTINGS", SettingsIcon.LOCATION, "location gps"),
        android("language", "Languages", "LOCALE_SETTINGS", SettingsIcon.SETTINGS, "language locale"),
        android("date", "Date and time", "DATE_SETTINGS", SettingsIcon.SETTINGS, "clock date time timezone"),
        android("vpn", "VPN", "VPN_SETTINGS", SettingsIcon.SHIELD, "vpn network"),
        android("defaultApps", "Default apps", "MANAGE_DEFAULT_APPS_SETTINGS", SettingsIcon.GRID, "default apps browser"),
        android("home", "Default launcher", "HOME_SETTINGS", SettingsIcon.HOME, "launcher home widgets"),
        android("search", "Search Android settings", "APP_SEARCH_SETTINGS", SettingsIcon.SETTINGS),
        android("root", "Android settings", "SETTINGS", SettingsIcon.SETTINGS),
    )
    fun entry(id: String) = grove.firstOrNull { it.id == id }
    fun destination(route: String?, anchor: String?): SettingsDestination.Grove? = grove
        .map { it.destination as SettingsDestination.Grove }
        .firstOrNull { it.route == route && it.anchor == anchor }
}

/** Small immutable catalogue, prepared once; never queries Android or protected sources. */
internal class SettingsMatcher(entries: List<SettingsEntry>) {
    private val prepared = entries.distinctBy { it.destination }.map { entry ->
        entry to (listOf(entry.title) + entry.aliases).map(::normalize)
    }
    fun matching(query: String, limit: Int = 6): List<SettingsEntry> {
        val preparedQuery = Search.prepare(normalize(query))
        if (preparedQuery.text.isEmpty() || limit <= 0) return emptyList()
        val candidates = if (preparedQuery.text in setOf("settings", "grove settings"))
            prepared.filter { it.first.id.startsWith("category-") } else prepared
        return candidates.map { (entry, labels) -> entry to labels.maxOf { Search.scoreNormalized(it, preparedQuery) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
    companion object {
        fun normalize(value: String) = Search.normalize(value).replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
    }
}

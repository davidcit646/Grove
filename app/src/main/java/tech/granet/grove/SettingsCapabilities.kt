package tech.granet.grove

internal data class SettingsHandler(val packageName: String, val enabled: Boolean,
    val applicationEnabled: Boolean, val exported: Boolean, val system: Boolean, val permitted: Boolean)
internal object SettingsCapabilities {
    fun trusted(handler: SettingsHandler) = handler.enabled && handler.applicationEnabled &&
        handler.exported && handler.system && handler.permitted
    fun allowed(handler: SettingsHandler, settingsPackages: Set<String>) =
        trusted(handler) && handler.packageName in settingsPackages
    fun available(entries: List<SettingsEntry>, resolve: (SettingsEntry) -> Boolean): List<SettingsEntry> =
        entries.filter { try { resolve(it) } catch (_: Exception) { false } }
    enum class Launch { OPENED, UNAVAILABLE, FAILED }
    fun <T> launch(resolve: () -> T?, open: (T) -> Unit): Launch = try {
        val target = resolve()
        if (target == null) Launch.UNAVAILABLE else { open(target); Launch.OPENED }
    } catch (_: Exception) { Launch.FAILED }
}

/** Provider order is structural, independent of a match's score. */
internal data class SettingsMatches(val grove: List<SettingsEntry>, val android: List<SettingsEntry>) {
    val ordered get() = grove + android
}

/** Generic Android search is a labeled fallback, not a match or a fabricated widget settings page. */
internal object SettingsSearchFallback {
    fun rows(matches: List<SettingsEntry>, available: List<SettingsEntry>): List<SettingsEntry> {
        val fallback = available.firstOrNull { it.id == "android-search" }
            ?: available.firstOrNull { it.id == "android-root" }
        return (matches.filter { it.id !in setOf("android-search", "android-root") }.take(5) + listOfNotNull(fallback))
            .distinctBy { it.destination }
    }
}

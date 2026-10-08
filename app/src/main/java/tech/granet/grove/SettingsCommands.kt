package tech.granet.grove

import android.content.Context

internal enum class SettingKey {
    APPS_BUTTON, SEARCH_BUTTON, CLOCK, CLOCK_ACTION, PINS, PIN_BOTTOM, WALLPAPER_COLORS,
    SWIPE_SEARCH, SWIPE_DRAWER, TAP_MENU, HOLD_MENU, CONTACTS, FILES, CONTACT_INDEX, FILE_INDEX, CALCULATOR, ANDROID_SETTINGS, GROVE_SETTINGS,
}
internal data class CommandFeedback(val saved: Boolean, val message: String? = null)

/** Typed preference commands. Separate stores and capability effects retain their own owners. */
internal class SettingsCommands(private val context: Context, private val repository: SettingsRepository) {
    fun toggle(key: SettingKey, value: Boolean): CommandFeedback = change { config ->
        when (key) {
            SettingKey.APPS_BUTTON -> config.copy(homeScreen = config.homeScreen.copy(showAppsButton = value))
            SettingKey.SEARCH_BUTTON -> config.copy(homeScreen = config.homeScreen.copy(showSearchButton = value))
            SettingKey.CLOCK -> config.copy(homeScreen = config.homeScreen.copy(showClock = value))
            SettingKey.CLOCK_ACTION -> config.copy(homeScreen = config.homeScreen.copy(tapClockOpensClock = value))
            SettingKey.PINS -> config.copy(homeScreen = config.homeScreen.copy(showPinnedApps = value))
            SettingKey.PIN_BOTTOM -> config.copy(homeScreen = config.homeScreen.copy(pinnedAppsAtBottom = value))
            SettingKey.WALLPAPER_COLORS -> config.copy(homeScreen = config.homeScreen.copy(useWallpaperButtonColors = value))
            SettingKey.SWIPE_SEARCH -> config.copy(gestures = config.gestures.copy(swipeDownSearch = value))
            SettingKey.SWIPE_DRAWER -> config.copy(gestures = config.gestures.copy(swipeUpAppDrawer = value))
            SettingKey.TAP_MENU -> config.copy(gestures = config.gestures.copy(tapHomeContextMenu = value))
            SettingKey.HOLD_MENU -> config.copy(gestures = config.gestures.copy(longPressHomeContextMenu = value))
            SettingKey.CONTACTS -> config.copy(search = config.search.copy(contacts = value))
            SettingKey.FILES -> config.copy(search = config.search.copy(files = value))
            SettingKey.CONTACT_INDEX -> config.copy(search = config.search.copy(contactIndexing = value))
            SettingKey.FILE_INDEX -> config.copy(search = config.search.copy(fileIndexing = value))
            SettingKey.CALCULATOR -> config.copy(search = config.search.copy(calculator = value))
            SettingKey.ANDROID_SETTINGS -> config.copy(search = config.search.copy(androidSettings = value))
            SettingKey.GROVE_SETTINGS -> config.copy(search = config.search.copy(groveSettings = value))
        }
    }
    fun theme(mode: ThemeMode) = change { it.copy(themeMode = mode) }
    fun grid(home: Boolean, grid: IconGrid?) = change { if (home) it.copy(homeGrid = grid) else it.copy(drawerGrid = grid) }
    private fun change(transform: (Config) -> Config): CommandFeedback {
        val before = try { repository.snapshot().config.search }
            catch (_: Exception) { return CommandFeedback(false, "Settings unavailable. Try again.") }
        return feedback(repository.update(change = transform), before)
    }
    fun replace(config: Config, revision: Long): CommandFeedback {
        val before = try { repository.snapshot().config.search }
            catch (_: Exception) { return CommandFeedback(false, "Settings unavailable. Try again.") }
        return feedback(repository.update(revision, true) { config }, before)
    }
    private fun feedback(outcome: SettingsOutcome, before: SearchSettings): CommandFeedback = when (outcome) {
        is SettingsOutcome.Saved -> {
            try {
                val after = outcome.snapshot.config.search
                val ready = SearchSettingsEffects.reconcile(before, after,
                    { IndexWork.reconcile(context, "contacts") }, { IndexWork.reconcile(context, "files") })
                CommandFeedback(true, if (ready) null else "Settings saved; index scheduling unavailable")
            } catch (_: Exception) { CommandFeedback(true, "Settings saved; background refresh unavailable") }
        }
        is SettingsOutcome.Invalid -> CommandFeedback(false, outcome.reason)
        is SettingsOutcome.Conflict -> CommandFeedback(false, "Settings changed. Review the latest values before applying.")
        is SettingsOutcome.Unavailable -> CommandFeedback(false, "Could not save settings. Your previous settings are still active.")
    }
}

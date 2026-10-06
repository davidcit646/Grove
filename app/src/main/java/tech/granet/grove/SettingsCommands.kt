package tech.granet.grove

import android.content.Context

internal enum class SettingKey {
    APPS_BUTTON, SEARCH_BUTTON, CLOCK, CLOCK_ACTION, PINS, PIN_HINT, PIN_BOTTOM, WALLPAPER_COLORS,
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
            SettingKey.PIN_HINT -> config.copy(homeScreen = config.homeScreen.copy(showPinnedAppsHint = value))
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
    fun searchReplayPending(): Boolean = context.getSharedPreferences("grove", Context.MODE_PRIVATE).getBoolean("search_tutorial_pending", false)
    fun replaySearch(enabled: Boolean): CommandFeedback = checked {
        context.getSharedPreferences("grove", Context.MODE_PRIVATE).edit().apply {
            if (enabled) {
                putBoolean("search_tutorial_pending", true)
                putLong("search_tutorial_request", System.nanoTime())
            } else remove("search_tutorial_pending")
        }.commit()
    }
    fun replayPending(): Boolean = context.getSharedPreferences("grove", Context.MODE_PRIVATE).getBoolean("setup_pending", false)
    fun replay(enabled: Boolean): CommandFeedback = checked {
        context.getSharedPreferences("grove", Context.MODE_PRIVATE).edit().apply {
            if (enabled) putBoolean("setup_pending", true) else remove("setup_pending")
        }.commit()
    }
    fun capture(enabled: Boolean) = checked { CrashReporter.setEnabled(context, enabled) }
    fun email(value: String) = checked { CrashReporter.setDeveloperEmail(context, value) }
    fun deleteReports() = checked { CrashReporter.deleteAll(context) }
    private fun checked(action: () -> Boolean): CommandFeedback = try {
        if (action()) CommandFeedback(true) else CommandFeedback(false, "Could not save this change. Try again.")
    } catch (_: Exception) { CommandFeedback(false, "This operation is unavailable. Try again.") }
    private val workStates = mutableMapOf<String, androidx.work.WorkInfo?>()
    fun observeIndex(kind: String, infos: List<androidx.work.WorkInfo>) {
        workStates[kind] = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(context, kind) }
    }
    fun indexStatus(kind: String, permitted: Boolean): String {
        val setting = repository.snapshot().config.search
        val enabled = if (kind == "files") setting.files else setting.contacts
        val indexed = if (kind == "files") setting.fileIndexing else setting.contactIndexing
        val cache = IndexCache.metadata(kind)
        return when {
            !enabled -> "Search disabled"
            !permitted -> "Android access required"
            !indexed -> "Live search · indexing off"
            cache.validity == IndexValidity.UNKNOWN -> "Checking saved index"
            cache.validity == IndexValidity.CORRUPT -> "Saved index invalid · live search available"
            cache.validity == IndexValidity.ABSENT -> "No saved index · live search available"
            !cache.fresh(kind) -> "Saved index needs a refresh"
            cache.partial -> "Partial saved index · live search available"
            else -> "Saved index available"
        }
    }
    fun indexActivity(kind: String, permitted: Boolean): String {
        val setting = repository.snapshot().config.search
        val eligible = if (kind == "files") IndexAccessPolicy.files(setting, permitted) else IndexAccessPolicy.contacts(setting, permitted)
        if (!eligible) return "Background refresh: Off"
        if (kind == "contacts") (context.applicationContext as GroveApp).contactChanges.failure.value?.let { return it }
        IndexWork.failures.value?.get(kind)?.let { return "Background refresh: $it" }
        val work = workStates[kind]
        return "Background refresh: " + when (work?.state) {
            androidx.work.WorkInfo.State.RUNNING -> "Running"
            androidx.work.WorkInfo.State.ENQUEUED -> if (work.runAttemptCount > 0) "Waiting to retry" else "Queued"
            androidx.work.WorkInfo.State.BLOCKED -> "Waiting"
            androidx.work.WorkInfo.State.FAILED -> "Failed · live search available"
            else -> "Idle"
        }
    }
    fun retryIndex(kind: String): CommandFeedback = try {
        if (kind !in listOf("contacts", "files")) CommandFeedback(false, "Unknown index")
        else if (IndexWork.enqueue(context, kind, IndexRefreshCause.MANUAL)) CommandFeedback(true, "Index refresh requested")
        else CommandFeedback(false, "Enable search and indexing, and allow Android access first.")
    } catch (_: Exception) { CommandFeedback(false, "Index refresh unavailable") }
}

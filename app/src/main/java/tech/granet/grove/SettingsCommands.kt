package tech.granet.grove

import android.content.Context

internal enum class SettingKey {
    APPS_BUTTON, SEARCH_BUTTON, CLOCK, CLOCK_ACTION, PINS, PIN_HINT, PIN_BOTTOM, WALLPAPER_COLORS,
    SWIPE_SEARCH, SWIPE_DRAWER, TAP_MENU, HOLD_MENU, CONTACTS, FILES, CONTACT_INDEX, FILE_INDEX,
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
        }
    }
    fun theme(mode: ThemeMode) = change { it.copy(themeMode = mode) }
    fun grid(home: Boolean, grid: IconGrid?) = change { if (home) it.copy(homeGrid = grid) else it.copy(drawerGrid = grid) }
    private fun change(transform: (Config) -> Config): CommandFeedback = feedback(repository.update(change = transform))
    fun replace(config: Config, revision: Long): CommandFeedback = feedback(repository.update(revision, true) { config })
    private fun feedback(outcome: SettingsOutcome): CommandFeedback = when (outcome) {
        is SettingsOutcome.Saved -> {
            try {
                val contacts = IndexWork.reconcile(context, "contacts")
                val files = IndexWork.reconcile(context, "files")
                CommandFeedback(true, if (contacts && files) null else "Settings saved; index scheduling unavailable")
            } catch (_: Exception) { CommandFeedback(true, "Settings saved; background refresh unavailable") }
        }
        is SettingsOutcome.Invalid -> CommandFeedback(false, outcome.reason)
        is SettingsOutcome.Conflict -> CommandFeedback(false, "Settings changed. Review the latest values before applying.")
        is SettingsOutcome.Unavailable -> CommandFeedback(false, "Could not save settings. Your previous settings are still active.")
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
        val file = java.io.File(context.filesDir, "grove-$kind-index.json")
        return when {
            !enabled -> "Search disabled"
            !permitted -> "Android access required"
            !indexed -> if (file.exists()) "Live search · cache deletion pending" else "Live search · indexing off"
            !file.exists() -> "No saved index · live search available"
            System.currentTimeMillis() - file.lastModified() !in 0..(if (kind == "files") 24L * 60 * 60_000 else 15L * 60_000) -> "Saved index needs a refresh"
            else -> "Saved index available"
        }
    }
    fun indexActivity(kind: String, permitted: Boolean): String {
        val setting = repository.snapshot().config.search
        val eligible = if (kind == "files") IndexAccessPolicy.files(setting, permitted) else IndexAccessPolicy.contacts(setting, permitted)
        if (!eligible) return "Background refresh: Off"
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
        else if (IndexWork.enqueue(context, kind)) CommandFeedback(true, "Index refresh queued")
        else CommandFeedback(false, "Enable search and indexing, and allow Android access first.")
    } catch (_: Exception) { CommandFeedback(false, "Index refresh unavailable") }
}

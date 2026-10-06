package tech.granet.grove

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import tech.granet.grove.ui.bodyText
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.iconRow
import tech.granet.grove.ui.message
import tech.granet.grove.ui.titleText

/** Category presentation. Commands are typed; this renderer never writes a preference. */
internal class SettingsPages(
    private val activity: SettingsActivity,
    private val session: SettingsSession,
    private val navigate: (String) -> Unit,
    private val platform: (String) -> Unit,
    private val access: (String) -> Unit,
    private val pickImport: () -> Unit,
    private val export: () -> Unit,
    private val chooseHome: () -> Unit,
    private val themeChanged: () -> Unit,
) {
    companion object {
        val routes = setOf("root", "home", "drawer", "search", "appearance", "configuration", "help", "about", "editor", "review", "recovery", "homeGrid", "drawerGrid", "email", "deleteReports", "defaults", "theme", "credits")
        fun title(route: String): String = when (route) {
            "root" -> "Launcher settings"; "home" -> "Home screen"; "drawer" -> "App drawer"
            "search" -> "Search"; "appearance" -> "Appearance"; "configuration" -> "Configuration"
            "help" -> "Help & diagnostics"; "about" -> "About Grove"; "editor" -> "Configuration editor"
            "review" -> "Review configuration"; "recovery" -> "Configuration recovery"
            "homeGrid" -> "Home grid"; "drawerGrid" -> "App drawer grid"; "email" -> "Developer email"
            "deleteReports" -> "Delete saved reports"; "defaults" -> "Restore configuration defaults"
            "theme" -> "Theme"; else -> "Artwork credits"
        }
    }
    private val statuses = mutableMapOf<String, TextView>()
    private val config get() = session.repository.snapshot().config
    private val documents by lazy { SettingsDocumentPages(activity, session, navigate, ::feedback) }
    private val grids by lazy { SettingsGridPage(activity, session, ::feedback) }
    fun render(route: String): LinearLayout {
        statuses.clear()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(activity.dp(20), activity.dp(8), activity.dp(20), activity.dp(24))
        }
        when (route) {
            "root" -> {
                row(content, "Home screen", "Controls, gestures and pinned apps", R.drawable.ic_setup_home) { navigate("home") }
                row(content, "App drawer", "Grid and folders", R.drawable.ic_setup_apps) { navigate("drawer") }
                row(content, "Search", "Contacts, files and indexing", R.drawable.ic_setup_search) { navigate("search") }
                row(content, "Appearance", "Theme, colors and wallpaper", R.drawable.ic_settings_palette) { navigate("appearance") }
                row(content, "Configuration", "Import, export and advanced editing", R.drawable.ic_settings_description) { navigate("configuration") }
                row(content, "Help & diagnostics", "Tutorials and local reports", R.drawable.ic_settings_help) { navigate("help") }
                row(content, "About", "Version, privacy and credits", R.drawable.ic_settings_info) { navigate("about") }
            }
            "home" -> {
                section(content, "Controls")
                toggle(content, "Show Apps button", config.homeScreen.showAppsButton, SettingKey.APPS_BUTTON)
                toggle(content, "Show Search button", config.homeScreen.showSearchButton, SettingKey.SEARCH_BUTTON)
                toggle(content, "Show clock and date", config.homeScreen.showClock, SettingKey.CLOCK)
                toggle(content, "Tap clock to open Clock", config.homeScreen.tapClockOpensClock, SettingKey.CLOCK_ACTION)
                section(content, "Pinned apps")
                toggle(content, "Show pinned apps", config.homeScreen.showPinnedApps, SettingKey.PINS)
                toggle(content, "Show pinned apps hint", config.homeScreen.showPinnedAppsHint, SettingKey.PIN_HINT)
                toggle(content, "Pinned apps at bottom", config.homeScreen.pinnedAppsAtBottom, SettingKey.PIN_BOTTOM)
                row(content, "Icon grid", gridLabel(config.homeGrid), R.drawable.ic_setup_apps) { navigate("homeGrid") }
                section(content, "Gestures")
                toggle(content, "Swipe down to search", config.gestures.swipeDownSearch, SettingKey.SWIPE_SEARCH)
                toggle(content, "Swipe up for app drawer", config.gestures.swipeUpAppDrawer, SettingKey.SWIPE_DRAWER)
                toggle(content, "Tap empty space for settings", config.gestures.tapHomeContextMenu, SettingKey.TAP_MENU)
                toggle(content, "Hold empty space for settings", config.gestures.longPressHomeContextMenu, SettingKey.HOLD_MENU)
                row(content, "Add widget", "Choose and configure on Home", R.drawable.ic_settings_widgets) { platform("widget") }
                statuses["role"] = activity.bodyText("").also(content::addView)
                row(content, "Default launcher", "Choose Grove in Android", R.drawable.ic_setup_home, chooseHome)
            }
            "drawer" -> {
                row(content, "Icon grid", gridLabel(config.drawerGrid), R.drawable.ic_setup_apps) { navigate("drawerGrid") }
                row(content, "Manage folders and apps", "Open drawer selection and folder actions", R.drawable.ic_setup_folder) { platform("folders") }
            }
            "search" -> {
                source(content, "contacts", "Contacts", SettingKey.CONTACTS, SettingKey.CONTACT_INDEX, config.search.contacts, config.search.contactIndexing)
                source(content, "files", "Files", SettingKey.FILES, SettingKey.FILE_INDEX, config.search.files, config.search.fileIndexing)
                content.addView(activity.bodyText("Indexes stay on this device. Turning indexing off deletes its cache; permitted search continues live."))
            }
            "appearance" -> {
                row(content, "Theme", config.themeMode.label, R.drawable.ic_settings_palette) { navigate("theme") }
                toggle(content, "Wallpaper colors for buttons", config.homeScreen.useWallpaperButtonColors, SettingKey.WALLPAPER_COLORS)
                content.addView(activity.bodyText("Applies in System mode. Wallpaper colors mode always uses Android’s Home wallpaper palette."))
                row(content, "Wallpaper", "Built-in artwork, your image or solid black", R.drawable.ic_settings_image) { platform("wallpaper") }
            }
            "theme" -> ThemeMode.entries.forEach { mode -> row(content, mode.label,
                if (mode == config.themeMode) "Selected" else null, R.drawable.ic_settings_palette) {
                if (feedback(session.commands.theme(mode))) themeChanged()
            } }
            "configuration", "editor", "review", "recovery", "defaults", "email", "deleteReports" -> documents.render(route, content, pickImport, export)
            "homeGrid", "drawerGrid" -> grids.render(route == "homeGrid", content)
            "help" -> {
                val pending = session.commands.replayPending()
                row(content, if (pending) "Cancel tutorial replay" else "Replay first-run setup", "Starts when you return Home", R.drawable.ic_settings_help) {
                    if (feedback(session.commands.replay(!session.commands.replayPending()))) navigate("help")
                }
                toggleAction(content, "Automatic crash reports", CrashReporter.isEnabled(activity)) { session.commands.capture(it) }
                content.addView(activity.bodyText("Reports stay on this device until you choose to share them."))
                row(content, "Developer email", CrashReporter.developerEmail(activity), R.drawable.ic_settings_mail) { navigate("email") }
                row(content, "Review saved reports", "${CrashReporter.pendingCount(activity)} saved", R.drawable.ic_settings_description) { CrashReporter.reviewPending(activity) }
                row(content, "Delete saved reports", null, R.drawable.ic_settings_delete) { navigate("deleteReports") }
            }
            "about" -> {
                section(content, "Grove ${BuildConfig.VERSION_NAME}")
                content.addView(activity.bodyText("Free and open source · Apache 2.0\nNo ads, analytics or automatic telemetry."))
                row(content, "Project", "Source and issues", R.drawable.ic_settings_code) { link("https://github.com/davidcit646/Grove") }
                row(content, "Privacy", "How Grove uses local data", R.drawable.ic_settings_shield) { link("https://github.com/davidcit646/Grove/blob/main/PRIVACY.md") }
                row(content, "Licenses", "Original and third-party attribution", R.drawable.ic_settings_description) { link("https://github.com/davidcit646/Grove/blob/main/NOTICE") }
                row(content, "Artwork credits", "Available offline", R.drawable.ic_settings_image) { navigate("credits") }
            }
            "credits" -> WallpaperArt.sources.forEach { source ->
                section(content, source.title)
                content.addView(activity.bodyText("${source.author} · ${source.license}"))
                source.changes?.let { content.addView(activity.bodyText(it)) }
                source.sourcePage?.let { url -> row(content, "Source", url, R.drawable.ic_setup_star) { link(url) } }
            }
        }
        refreshStatus(); return content
    }
    private fun source(content: LinearLayout, kind: String, title: String, search: SettingKey, index: SettingKey, enabled: Boolean, indexed: Boolean) {
        val group = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12))
        }
        val card = MaterialCardView(activity).apply {
            radius = activity.dp(20).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant))
            addView(group)
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(16) })
        group.addView(activity.iconRow(title, if (kind == "contacts") R.drawable.ic_setup_person else R.drawable.ic_settings_description,
            minHeightDp = 48, onClick = {}).apply { isClickable = false; isFocusable = false })
        toggle(group, "Search ${title.lowercase()}", enabled, search) { if (it && !activity.permitted(kind)) access(kind) }
        group.addView(activity.bodyText(if (kind == "contacts") "We need contact access for contact search to work." else "We need file access for file search to work."))
        val accessRow = activity.iconRow("Android access", R.drawable.ic_settings_shield,
            minHeightDp = 56, onClick = { access(kind) })
        group.addView(accessRow)
        statuses["access-$kind"] = activity.bodyText("").also(group::addView)
        toggle(group, "Background indexing", indexed, index) { if (it && (if (kind == "contacts") config.search.contacts else config.search.files) && !activity.permitted(kind)) access(kind) }
        statuses[kind] = activity.bodyText("").also(group::addView)
        statuses["work-$kind"] = activity.bodyText("").also(group::addView)
        group.addView(MaterialButton(activity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Refresh index"; setIconResource(R.drawable.ic_settings_refresh)
            setOnClickListener { feedback(session.commands.retryIndex(kind)); refreshStatus() }
        })
    }
    private fun toggle(content: LinearLayout, title: String, checked: Boolean, key: SettingKey, after: (Boolean) -> Unit = {}) =
        toggleAction(content, title, checked) { value -> session.commands.toggle(key, value).also { if (it.saved) after(value) } }
    private fun toggleAction(content: LinearLayout, title: String, checked: Boolean, changed: (Boolean) -> CommandFeedback) {
        var reverting = false
        content.addView(MaterialSwitch(activity).apply {
            text = title; isChecked = checked; minimumHeight = activity.dp(56)
            setOnCheckedChangeListener { _, value ->
                if (!reverting) {
                    val result = changed(value)
                    if (!feedback(result)) { reverting = true; isChecked = !value; reverting = false }
                    refreshStatus()
                }
            }
        })
    }
    fun refreshStatus() {
        if (statuses.isEmpty()) return
        try {
            for (kind in listOf("contacts", "files")) {
                statuses[kind]?.text = session.commands.indexStatus(kind, activity.permitted(kind))
                statuses["work-$kind"]?.text = session.commands.indexActivity(kind, activity.permitted(kind))
                statuses["access-$kind"]?.text = if (activity.permitted(kind)) "Allowed · tap Android access to manage" else "Not allowed · tap Android access to enable"
            }
            statuses["role"]?.text = if (activity.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME))
                "Grove is your default launcher" else "Grove is not your default launcher"
        } catch (_: Exception) { statuses.values.forEach { it.text = "Status unavailable; try again" } }
    }
    private fun feedback(result: CommandFeedback): Boolean { result.message?.let(activity::message); return result.saved }
    private fun section(content: LinearLayout, title: String) { content.addView(activity.titleText(title, 16)) }
    private fun row(content: LinearLayout, title: String, subtitle: String?, icon: Int, action: () -> Unit) {
        content.addView(activity.iconRow(title, icon, subtitle, minHeightDp = 72, onClick = action).apply { isFocusable = true })
    }
    private fun link(url: String) = activity.openLink(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    private fun gridLabel(grid: IconGrid?) = grid?.let { "${it.columns} × ${it.rows}" } ?: "Automatic"
}

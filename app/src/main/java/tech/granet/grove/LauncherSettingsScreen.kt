package tech.granet.grove

import android.text.InputType
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.addSection
import tech.granet.grove.ui.bodyText
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.inputDialog
import tech.granet.grove.ui.message
import tech.granet.grove.ui.scrollDialog
import tech.granet.grove.ui.settingsButton
import tech.granet.grove.ui.toggleRow
import tech.granet.grove.ui.warningText

/** Builds launcher preferences independently of home navigation. */
internal class LauncherSettingsScreen(
    private val activity: AppCompatActivity,
    private val current: () -> Config,
    private val update: (Config) -> Unit,
    private val editCustom: () -> Unit,
    private val exportConfig: () -> Unit,
    private val importConfig: () -> Unit,
    private val tutorialsPending: () -> Boolean,
    private val resetTutorials: (Boolean) -> Unit,
    private val onClose: () -> Unit,
    private val indexStatus: (String) -> String,
    private val retryIndex: (String) -> Unit,
) {
    fun show() {
        val config = current()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(4), activity.dp(20), activity.dp(4))
        }

        fun toggle(text: String, checked: Boolean, changed: (Boolean) -> Unit) {
            content.addView(activity.toggleRow(text, checked, changed),
                LinearLayout.LayoutParams(-1, -2))
        }

        fun commit(next: Config) {
            update(next)
        }

        content.addSection("Gestures")
        toggle("Swipe down to search", config.gestures.swipeDownSearch) { enabled ->
            commit(current().copy(gestures = current().gestures.copy(swipeDownSearch = enabled)))
        }
        toggle("Swipe up for app drawer", config.gestures.swipeUpAppDrawer) { enabled ->
            commit(current().copy(gestures = current().gestures.copy(swipeUpAppDrawer = enabled)))
        }
        content.addSection("Search")
        toggle("Contact search", config.search.contacts) { enabled ->
            commit(current().copy(search = current().search.copy(contacts = enabled)))
        }
        toggle("File search (shared storage)", config.search.files) { enabled ->
            commit(current().copy(search = current().search.copy(files = enabled)))
        }
        content.addSection("Background indexing")
        toggle("Index contacts", config.search.contactIndexing) { enabled ->
            commit(current().copy(search = current().search.copy(contactIndexing = enabled)))
        }
        toggle("Index shared-storage files", config.search.fileIndexing) { enabled ->
            commit(current().copy(search = current().search.copy(fileIndexing = enabled)))
        }
        content.addView(activity.bodyText("Contacts: ${indexStatus("contacts")} · Files: ${indexStatus("files")}"))
        content.addView(activity.settingsButton("Retry contact index", R.drawable.ic_contact) { retryIndex("contacts") })
        content.addView(activity.settingsButton("Retry file index", R.drawable.ic_folder) { retryIndex("files") })
        content.addView(activity.bodyText("Indexes store names and file paths in Grove's private storage for faster search. They run in the background when Android permits access. Search can still work without an index, though file lookup may be slower or partial. Turning indexing off deletes that index. Android permissions stay granted until you revoke them in system settings."))
        content.addSection("Home screen")
        toggle("Show Apps button", config.homeScreen.showAppsButton) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(showAppsButton = enabled)))
        }
        toggle("Show Search button", config.homeScreen.showSearchButton) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(showSearchButton = enabled)))
        }
        toggle("Show clock and date", config.homeScreen.showClock) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(showClock = enabled)))
        }
        toggle("Tap clock to open Clock", config.homeScreen.tapClockOpensClock) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(tapClockOpensClock = enabled)))
        }
        toggle("Show pinned apps", config.homeScreen.showPinnedApps) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(showPinnedApps = enabled)))
        }
        toggle("Show pinned apps hint", config.homeScreen.showPinnedAppsHint) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(showPinnedAppsHint = enabled)))
        }
        toggle("Use wallpaper colors for buttons", config.homeScreen.useWallpaperButtonColors) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(useWallpaperButtonColors = enabled)))
        }
        content.addView(activity.bodyText("On uses colors from your selected wallpaper for Grove’s Home, Search, and All apps buttons. Off uses Grove’s system light and dark colors."))
        toggle("Pinned apps at bottom", config.homeScreen.pinnedAppsAtBottom) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(pinnedAppsAtBottom = enabled)))
        }

        content.addSection("Tutorials")
        toggle("Replay tutorials on next Home", tutorialsPending()) { enabled ->
            resetTutorials(enabled)
        }
        content.addView(activity.bodyText("Turning this on restarts the full setup when you return Home and shows the widget tip again. Turn it off before leaving settings to cancel. Your current choices stay in place until you finish setup."))

        content.addSection("Crash reports")
        toggle("Crash reporting", CrashReporter.isEnabled(activity)) { enabled ->
            CrashReporter.setEnabled(activity, enabled)
        }
        content.addView(activity.bodyText("If Grove crashes or hits an error, a report is saved on this device only. On the next launch you'll be asked whether to email it to the developer. Nothing is ever sent automatically, and no third-party service is involved."))
        lateinit var emailButton: com.google.android.material.button.MaterialButton
        emailButton = activity.settingsButton(
            "Developer email: ${CrashReporter.developerEmail(activity).ifBlank { "not set" }}",
            R.drawable.ic_message,
        ) {
            activity.inputDialog(
                title = "Developer email",
                initial = CrashReporter.developerEmail(activity),
                hint = "you@example.com",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ) { email ->
                CrashReporter.setDeveloperEmail(activity, email)
                emailButton.text = "Developer email: ${email.ifBlank { "not set" }}"
                activity.message("Developer email saved")
            }
        }
        content.addView(emailButton)
        val pending = CrashReporter.pendingCount(activity)
        lateinit var deleteButton: com.google.android.material.button.MaterialButton
        deleteButton = activity.settingsButton(
            "Delete pending reports ($pending)",
            R.drawable.ic_delete,
        ) {
            val count = CrashReporter.pendingCount(activity)
            if (count == 0) { activity.message("No pending reports"); return@settingsButton }
            activity.confirmDialog(
                title = "Delete reports?",
                message = "Delete all $count saved problem ${if (count == 1) "report" else "reports"}?",
                positive = "Delete",
            ) {
                CrashReporter.deleteAll(activity)
                deleteButton.text = "Delete pending reports (0)"
            }
        }
        content.addView(deleteButton)

        content.addSection("Advanced")
        content.addView(activity.warningText("Changing the configuration directly is dangerous and can break Grove or make it unusable. Only edit this if you know what you’re doing."))
        content.addView(activity.settingsButton("Edit configuration", R.drawable.ic_settings) { editCustom() })
        content.addView(activity.settingsButton("Export configuration", R.drawable.ic_export) { exportConfig() })
        content.addView(activity.settingsButton("Import configuration", R.drawable.ic_import) { importConfig() })

        content.addView(activity.bodyText("Tap and hold empty home space to open this menu when enabled. If all controls are hidden, open Grove from another launcher’s app list or its Android app settings shortcut to restore them. Widgets remain until removed.")
            .apply { setPadding(0, activity.dp(16), 0, activity.dp(12)) })
        activity.scrollDialog("Launcher settings", content).setOnDismissListener { onClose() }
    }
}

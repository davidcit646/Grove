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
    private val update: (Config) -> Boolean,
    private val editCustom: () -> Unit,
    private val exportConfig: () -> Unit,
    private val importConfig: () -> Unit,
    private val tutorialsPending: () -> Boolean,
    private val resetTutorials: (Boolean) -> Boolean,
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

        fun toggle(text: String, checked: Boolean, changed: (Boolean) -> Boolean) {
            val control = activity.toggleRow(text, checked) { }
            var reverting = false
            control.setOnCheckedChangeListener { _, value ->
                if (!reverting && !changed(value)) {
                    reverting = true
                    control.isChecked = !value
                    reverting = false
                }
            }
            content.addView(control, LinearLayout.LayoutParams(-1, -2))
        }

        fun commit(next: Config): Boolean = update(next)

        content.addSection("Appearance")
        lateinit var themeButton: com.google.android.material.button.MaterialButton
        themeButton = activity.settingsButton("Theme: ${current().themeMode.label}", R.drawable.ic_wallpaper) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle("Theme")
                .setSingleChoiceItems(ThemeMode.entries.map { it.label }.toTypedArray(), current().themeMode.ordinal) { dialog, index ->
                    if (commit(current().copy(themeMode = ThemeMode.entries[index]))) {
                        themeButton.text = "Theme: ${current().themeMode.label}"
                        dialog.dismiss()
                    }
                }.setNegativeButton("Cancel", null).show()
        }
        content.addView(themeButton)
        content.addView(activity.bodyText("System follows Android. Light and Dark set Grove's interface appearance. Wallpaper colors follows Android's theme and uses the applied Home wallpaper's colors for launcher buttons; unavailable colors fall back to the theme. Android displays your actual wallpaper, including changes made outside Grove."))
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
        content.addView(activity.bodyText("Indexes store names and file paths in Grove's private storage for faster search. They run when the matching search source is enabled and Android permits access. Search can still work without an index, though file lookup may be slower or partial. Turning indexing off deletes that index. Android permissions stay granted until you revoke them in system settings."))
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
        content.addView(activity.bodyText("In System mode, this uses colors from Android’s applied Home wallpaper. Light and Dark use their theme colors; Wallpaper colors mode always uses the wallpaper palette."))
        toggle("Pinned apps at bottom", config.homeScreen.pinnedAppsAtBottom) { enabled ->
            commit(current().copy(homeScreen = current().homeScreen.copy(pinnedAppsAtBottom = enabled)))
        }

        content.addSection("Tutorials")
        lateinit var replayButton: com.google.android.material.button.MaterialButton
        fun refreshReplayButton() {
            replayButton.text = if (tutorialsPending()) "Cancel tutorial replay" else "Replay first-run setup"
        }
        replayButton = activity.settingsButton(
            if (tutorialsPending()) "Cancel tutorial replay" else "Replay first-run setup",
            R.drawable.ic_info,
        ) {
            val next = !tutorialsPending()
            val committed = resetTutorials(next)
            refreshReplayButton()
            if (committed) activity.message(if (next) "Tutorial replay queued for next Home" else "Tutorial replay canceled")
        }
        content.addView(replayButton)
        content.addView(activity.bodyText(
            "Replay is one queued request: it begins when you return Home, can be canceled here before leaving settings, and does not change current pins, gestures, or search choices until setup finishes successfully."
        ))

        content.addSection("Crash reports")
        toggle("Crash reporting", CrashReporter.isEnabled(activity)) { enabled ->
            val saved = CrashReporter.setEnabled(activity, enabled)
            if (!saved) activity.message("Could not save crash reporting preference")
            saved
        }
        content.addView(activity.bodyText("If Grove crashes or hits an error, a report is saved on this device only. On the next launch you'll be asked whether to email it to the developer. Nothing is ever sent automatically, and no third-party service is involved."))
        lateinit var emailButton: com.google.android.material.button.MaterialButton
        emailButton = activity.settingsButton(
            "Developer email: ${CrashReporter.developerEmail(activity)}",
            R.drawable.ic_message,
        ) {
            activity.inputDialog(
                title = "Developer email",
                initial = CrashReporter.developerEmail(activity),
                hint = "you@example.com",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ) { email ->
                if (CrashReporter.setDeveloperEmail(activity, email)) {
                    emailButton.text = "Developer email: ${CrashReporter.developerEmail(activity)}"
                    activity.message("Developer email saved")
                } else activity.message("Could not save developer email")
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
                val removed = CrashReporter.deleteAll(activity)
                deleteButton.text = "Delete pending reports (${CrashReporter.pendingCount(activity)})"
                if (!removed) activity.message("Some reports could not be deleted")
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

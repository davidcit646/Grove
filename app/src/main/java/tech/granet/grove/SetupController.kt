package tech.granet.grove

import android.app.role.RoleManager
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.message
import java.util.*

/** Settings and first-run setup. Failed config persistence preserves the setup instance and reopens it for retry. */
internal class SetupController(private val activity: MainActivity) {
    internal var firstRunSetup: FirstRunSetup? = null

    fun setupPending(): Boolean = with(activity) { runCatching { prefs.getBoolean("setup_pending", false) }
        .onFailure { Log.w("Grove", "Setup flag unavailable", it) }.getOrDefault(false)
    }

    fun settings() {
        with(activity) {
            actionController.showActionMenu("Grove settings", listOf(
                Triple("Launcher settings", R.drawable.ic_settings) { launcherSettings() },
                Triple("Set as default launcher", R.drawable.ic_launcher) {
                    val role = getSystemService(RoleManager::class.java)
                    if (role.isRoleAvailable(RoleManager.ROLE_HOME))
                        chooseHome.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                },
                Triple("Add widget", R.drawable.ic_widget) { widgetFlow.pick() },
                Triple("Wallpapers", R.drawable.ic_wallpaper) { wallpaperPresentationController.wallpapers() },
                Triple("Privacy policy", R.drawable.ic_info) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/davidcit646/Grove/blob/main/PRIVACY.md")))
                },
                Triple("About Grove", R.drawable.ic_info) {
                    infoDialog("Grove · ${BuildConfig.VERSION_NAME}",
                        "A quiet place to start.\n\nFree and open source · Apache 2.0\nNo telemetry. Built-in wallpapers work offline; custom images stay in Grove's private on-device storage.\n\nSwipe down for search and swipe up for all apps when enabled. Swipe down from the top of the app drawer to close it. Hold and drag pinned apps to reorder them. Pinned apps can be placed near the top or bottom of Home.",
                        "Done")
                },
            ))
        }
    }

    fun startFirstRunSetup() {
        with(activity) {
            if (firstRunSetup != null) return
            if (catalogController.apps.isEmpty()) {
                if (setupPending()) message("Tutorial replay will start when apps are available")
                return
            }
            if (setupPending())
                prefs.edit().remove("widget_tutorial_seen").apply()
            firstRunSetup = FirstRunSetup(
                this, surface, configController.config, catalogController.apps.map { it.key to it.label },
                searchController::hasContactAccess, { Environment.isExternalStorageManager() },
                { SearchSourceState.resolve(true, searchController.hasContactAccess(), searchController.indexingContacts,
                    searchController.contactLoadFailed, searchController.contacts.size) },
                { SearchSourceState.resolve(true, Environment.isExternalStorageManager(), searchController.indexingFiles,
                    searchController.fileLoadFailed, searchController.files.size, searchController.fileScanSkipped) },
                searchController::explainContactAccess, searchController::explainFileAccess,
                finishSetup@{ next ->
                    val previousSearch = configController.config.search
                    if (!configController.commitConfig(next)) {
                        firstRunSetup?.show()
                        return@finishSetup
                    }
                    firstRunSetup = null
                    searchController.applySearchSettings(previousSearch)
                    prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").apply()
                    homeController.showHome()
                    val role = getSystemService(RoleManager::class.java)
                    if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Use Grove as your home screen?")
                            .setMessage("Android will ask you to choose a Home app. You can switch back in Android Settings at any time.")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Choose Home app") { _, _ ->
                                chooseHome.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                            }.show()
                    }
                },
                skipSetup@{
                    if (setupPending() && configController.config.favorites.isEmpty()) {
                        if (!configController.commitConfig(configController.config.copy(favorites = catalogController.apps.take(8).map { it.key }))) {
                            firstRunSetup?.show()
                            return@skipSetup
                        }
                        homeController.showHome()
                    }
                    firstRunSetup = null
                    prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").apply()
                },
            ).also { it.show() }
        }
    }

    fun launcherSettings() {
        with(activity) {
            LauncherSettingsScreen(
                this, { configController.config },
                { next ->
                    val previousSearch = configController.config.search
                    if (configController.commitConfig(next)) {
                        if (previousSearch != next.search) searchController.applySearchSettings(previousSearch)
                        if (!drawer) homeController.showHome()
                    }
                },
                { configController.editConfig() },
                { export.launch("grove-config.json") },
                { importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                { setupPending() },
                { enabled ->
                    val saved = prefs.edit().apply {
                        putBoolean("setup_complete", !enabled)
                        if (enabled) putBoolean("setup_pending", true)
                        else remove("setup_pending")
                    }.commit()
                    if (!saved) GroveErrorPresenter.show(this, GroveErrorRegistry.TUTORIAL_REPLAY)
                    saved
                },
                {
                    if (setupPending()) root.post {
                        if (!isDestroyed && setupPending()) {
                            homeController.showHome()
                            startFirstRunSetup()
                        }
                    }
                },
                { kind -> searchController.sources.status(kind) },
                { kind ->
                    if (kind == "files") searchController.indexFiles() else searchController.refreshContacts()
                },
            ).show()
        }
    }
}

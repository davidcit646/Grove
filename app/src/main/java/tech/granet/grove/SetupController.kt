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
    private var restoredSetup: Bundle? = null
    fun restore(state: Bundle?) { restoredSetup = state?.getBundle("firstRun") }
    fun saveState(state: Bundle) { (firstRunSetup?.saveState() ?: restoredSetup)?.let { state.putBundle("firstRun", it) } }
    fun destroy() { firstRunSetup?.destroy(); firstRunSetup = null }

    fun setupPending(): Boolean = with(activity) { runCatching { prefs.getBoolean("setup_pending", false) }
        .onFailure { Log.w("Grove", "Setup flag unavailable", it) }.getOrDefault(false)
    }

    fun settings() = launcherSettings()

    fun startFirstRunSetup() {
        with(activity) {
            when (TutorialReplayPolicy.decide(
                pending = setupPending(),
                setupShowing = firstRunSetup != null,
                appsAvailable = catalogController.apps.isNotEmpty(),
            )) {
                TutorialReplayDecision.NONE -> return
                TutorialReplayDecision.DEFER -> {
                    message("Tutorial replay will start when apps are available")
                    return
                }
                TutorialReplayDecision.START -> Unit
            }
            val setupPreviouslyCompleted = runCatching { prefs.getBoolean("setup_complete", false) }
                .onFailure { Log.w("Grove", "Setup completion state unavailable", it) }
                .getOrDefault(false)
            prefs.edit().remove("widget_tutorial_seen").apply()
            firstRunSetup = FirstRunSetup(
                this, surface, root, configController.config, catalogController.apps.map { it.key to it.label },
                searchController::hasContactAccess, { Environment.isExternalStorageManager() },
                searchController::requestContactAccess, searchController::requestFileAccess,
                !setupPreviouslyCompleted, restoredSetup,
                { done -> wallpaperController.background(0, resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels) { bitmap, _ -> done(bitmap) } },
                finishSetup@{ next ->
                    if (!configController.commitConfig(next)) {
                        firstRunSetup?.show()
                        return@finishSetup
                    }
                    if (!prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").commit()) {
                        GroveErrorPresenter.show(this, GroveErrorRegistry.TUTORIAL_REPLAY)
                        firstRunSetup?.show()
                        return@finishSetup
                    }
                    firstRunSetup = null
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
                    if (TutorialReplayPolicy.shouldSeedFavoritesOnSkip(
                            setupPreviouslyCompleted,
                            configController.config.favorites.isEmpty(),
                        )) {
                        if (!configController.commitConfig(configController.config.copy(favorites = catalogController.apps.take(8).map { it.key }))) {
                            firstRunSetup?.show()
                            return@skipSetup
                        }
                        homeController.showHome()
                    }
                    if (!prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").commit()) {
                        GroveErrorPresenter.show(this, GroveErrorRegistry.TUTORIAL_REPLAY)
                        firstRunSetup?.show()
                        return@skipSetup
                    }
                    firstRunSetup = null
                },
            ).also { restoredSetup = null; it.show() }
        }
    }

    fun launcherSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }
}

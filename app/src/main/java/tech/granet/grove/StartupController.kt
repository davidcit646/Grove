package tech.granet.grove

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Essential startup checks and recovery. Failed config or launcher service prevents downstream normal Home startup. */
internal class StartupController(private val activity: MainActivity) {
    internal var startupState: Bundle? = null
    internal var coreRecoveryState: CoreRecoveryState? = null
        private set
    internal val coreRecoveryVisible get() = coreRecoveryState != null
    internal var launcherCallbackRegistered = false

    fun beginHome() {
        with(activity) {
            val loaded = runCatching { configController.load() }.getOrElse { error ->
                Log.e("Grove", "Configuration unavailable", error)
                showCoreRecovery(CoreRecoveryReason.CONFIG)
                return
            }
            presentationController.applyTheme(loaded.themeMode)
            // Widget metadata is optional. Keep the app list and Home available if it is damaged.
            runCatching { widgets.restore(startupState) }
                .onFailure { Log.w("Grove", "Widget state unavailable", it) }
            if (!ensureLauncherCallback()) return
            clearCoreRecovery()
            root.setBackgroundColor(Color.TRANSPARENT)
            homeController.homeScrollY = startupState?.getInt("homeScrollY") ?: homeController.homeScrollY
            startupState = null
            homeController.showHome()
            applyStartupPlan(StartupCoordinator.coldStart())
            intent.getStringExtra("settingsAction")?.let { action ->
                intent.removeExtra("settingsAction")
                root.post { when (action) {
                    "wallpaper" -> wallpaperPresentationController.wallpapers()
                    "widget" -> widgetFlow.pick()
                    "folders" -> { drawerController.showDrawer(false); drawerController.drawerOptions() }
                } }
            }
            if (configController.configStore.brokenCustomConfig != null) root.post { configController.showConfigRecoveryDialog() }
            if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) root.post { setupController.settings() }
        }
    }

    fun ensureLauncherCallback(): Boolean {
        with(activity) {
            if (launcherCallbackRegistered) return true
            return try {
                launcher.registerCallback(changes, Handler(Looper.getMainLooper()))
                launcherCallbackRegistered = true
                true
            } catch (error: Exception) {
                Log.e("Grove", "Launcher service unavailable", error)
                showCoreRecovery(CoreRecoveryReason.LAUNCHER_SERVICE)
                false
            }
        }
    }

    fun clearCoreRecovery() {
        coreRecoveryState = null
    }

    fun showCoreRecovery(reason: CoreRecoveryReason) {
        with(activity) {
            searchTutorialController.destroy()
            val recovery = CoreRecoveryPolicy.forReason(reason)
            coreRecoveryState = recovery
            // Supersede pending catalog/search output before showing a closed core lane.
            catalogController.loadGeneration++
            searchController.cancelPending()
            drawer = false
            searchMode = false
            CoreRecoveryView.render(activity, recovery, ::beginHome)
        }
    }

    fun applyStartupPlan(plan: StartupCoordinator.Plan) {
        with(activity) {
            if (plan.loadApps) catalogController.loadApps()
            if (plan.reconcileIndexes) root.postDelayed({
                if (!isDestroyed && !coreRecoveryVisible) searchController.reconcileAccess()
            }, 150L)
        }
    }
}

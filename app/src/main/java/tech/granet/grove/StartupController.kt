package tech.granet.grove

import android.content.*
import android.graphics.*
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.message
import java.util.*

/** Essential startup checks and recovery. Failed config or launcher service prevents downstream normal Home startup. */
internal class StartupController(private val activity: MainActivity) {
    internal var startupState: Bundle? = null
    internal var coreRecoveryVisible = false
    internal var launcherCallbackRegistered = false

    fun beginHome() {
        with(activity) {
            val loaded = runCatching { configController.configStore.load() }.getOrElse { error ->
                Log.e("Grove", "Configuration unavailable", error)
                showCoreRecovery("Grove could not load its settings. Retry, or change your Home app in Android Settings. Your saved settings have not been erased.")
                return
            }
            configController.config = loaded
            // Widget metadata is optional. Keep the app list and Home available if it is damaged.
            runCatching { widgets.restore(startupState) }
                .onFailure { Log.w("Grove", "Widget state unavailable", it) }
            if (!ensureLauncherCallback()) return
            coreRecoveryVisible = false
            root.setBackgroundColor(Color.TRANSPARENT)
            homeController.homeScrollY = startupState?.getInt("homeScrollY") ?: homeController.homeScrollY
            startupState = null
            homeController.showHome()
            applyStartupPlan(StartupCoordinator.coldStart(startupSnapshot()))
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
                showCoreRecovery("Grove could not connect to Android's app launcher service.")
                false
            }
        }
    }

    fun showCoreRecovery(detail: String) {
        with(activity) {
            coreRecoveryVisible = true
            // Supersede pending catalog/search output before showing a closed core lane.
            catalogController.loadGeneration++
            searchController.cancelPending()
            drawer = false
            searchMode = false
            root.animate().cancel()
            root.removeAllViews()
            root.setBackgroundColor(0xff182421.toInt())
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
            panel.addView(TextView(this).apply {
                text = "Grove cannot load Home"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            panel.addView(TextView(this).apply {
                text = detail
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            panel.addView(Button(this).apply {
                text = "Retry"
                setOnClickListener { beginHome() }
            })
            panel.addView(Button(this).apply {
                text = "Android Home settings"
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {
                            message("Android Settings is unavailable")
                        }
                    }
                }
            })
            root.addView(panel, LinearLayout.LayoutParams(-1, -1))
        }
    }

    fun startupSnapshot(): StartupCoordinator.Snapshot = with(activity) { StartupCoordinator.Snapshot(
        contactSearchEnabled = configController.config.search.contacts,
        contactsGranted = searchController.hasContactAccess(),
        lastContactRefreshMs = searchController.lastContactRefresh,
        indexingContacts = searchController.indexingContacts,
        contactLoadFailed = searchController.contactLoadFailed,
        fileSearchEnabled = configController.config.search.files,
        filesGranted = Environment.isExternalStorageManager(),
        hasFiles = searchController.files.isNotEmpty(),
        indexingFiles = searchController.indexingFiles,
    )
    }

    fun applyStartupPlan(plan: StartupCoordinator.Plan) {
        with(activity) {
            if (plan.clearFiles) searchController.sources.clearFiles()
            if (plan.loadApps) catalogController.loadApps()
            if (plan.indexFiles) searchController.indexFiles()
            if (plan.refreshContacts) searchController.refreshContacts()
        }
    }
}

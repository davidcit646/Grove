package tech.granet.grove

import android.content.*
import android.graphics.*
import android.provider.Settings
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.message

internal enum class CoreRecoveryReason {
    CONFIG,
    LAUNCHER_SERVICE,
    APP_CATALOG,
}

internal data class CoreRecoveryState(
    val reason: CoreRecoveryReason,
    val detail: String,
    val retryable: Boolean = true,
    val settingsEscape: Boolean = true,
)

internal object CoreRecoveryPolicy {
    fun forReason(reason: CoreRecoveryReason): CoreRecoveryState = CoreRecoveryState(
        reason = reason,
        detail = when (reason) {
            CoreRecoveryReason.CONFIG -> GroveErrorRegistry.CONFIG_LOAD.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings. Your saved settings have not been erased."
            }
            CoreRecoveryReason.LAUNCHER_SERVICE -> GroveErrorRegistry.LAUNCHER_SERVICE.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings."
            }
            CoreRecoveryReason.APP_CATALOG -> GroveErrorRegistry.APP_CATALOG.let {
                "${it.feature} · ${it.severity.label}\n${it.codeLine()}\n\n${it.summary} Retry, or change your Home app in Android Settings."
            }
        },
    )
}

/** Essential startup checks and recovery. Failed config or launcher service prevents downstream normal Home startup. */
internal class StartupController(private val activity: MainActivity) {
    internal var startupState: Bundle? = null
    internal var coreRecoveryState: CoreRecoveryState? = null
        private set
    internal val coreRecoveryVisible get() = coreRecoveryState != null
    internal var launcherCallbackRegistered = false

    fun beginHome() {
        with(activity) {
            val loaded = runCatching { configController.configStore.load() }.getOrElse { error ->
                Log.e("Grove", "Configuration unavailable", error)
                showCoreRecovery(CoreRecoveryReason.CONFIG)
                return
            }
            configController.config = loaded
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
            val recovery = CoreRecoveryPolicy.forReason(reason)
            coreRecoveryState = recovery
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
                text = recovery.detail
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            if (recovery.retryable) panel.addView(Button(this).apply {
                text = "Retry"
                setOnClickListener { beginHome() }
            })
            if (recovery.settingsEscape) panel.addView(Button(this).apply {
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

    fun applyStartupPlan(plan: StartupCoordinator.Plan) {
        with(activity) {
            if (plan.loadApps) catalogController.loadApps()
            if (plan.reconcileIndexes) root.postDelayed({
                if (!isDestroyed && !coreRecoveryVisible) searchController.reconcileAccess()
            }, 150L)
        }
    }
}

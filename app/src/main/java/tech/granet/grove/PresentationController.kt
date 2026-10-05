package tech.granet.grove

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.Executors

/** Android renders the applied wallpaper. Only small color metadata is observed/cached here. */
internal class PresentationController(private val activity: MainActivity) {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { WallpaperManager.getInstance(activity) }
    private var observing = false
    private var generation = 0
    private var destroyed = false
    private var lastSnapshot: Pair<Int?, Int?>? = null
    var buttonColors: Pair<Int, Int>? = null; private set
    private val listener = WallpaperManager.OnColorsChangedListener { _, which ->
        if (PresentationPolicy.sourceChanged(which, WallpaperManager.FLAG_SYSTEM)) refresh()
    }

    fun start() {
        if (!observing) try {
            manager.addOnColorsChangedListener(listener, handler)
            observing = true
        } catch (error: Exception) {
            Log.w("Grove", "Wallpaper listener unavailable; refreshing on resume", error)
        }
        refresh()
    }

    fun applyTheme(mode: ThemeMode) {
        val night = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (activity.delegate.localNightMode != night) activity.delegate.localNightMode = night
    }

    fun refresh() {
        if (destroyed || worker.isShutdown) return
        val token = ++generation
        worker.execute {
            val snapshot = runCatching<Pair<Int?, Int?>> {
                manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) to
                    manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            }.onFailure { Log.w("Grove", "Wallpaper colors unavailable; using theme colors", it) }
                .getOrDefault(null to null)
            handler.post {
                if (destroyed || activity.isDestroyed || token != generation) return@post
                if (snapshot != lastSnapshot) {
                    lastSnapshot = snapshot
                    buttonColors = snapshot.second?.let { color -> color to
                        (if (Color.luminance(color) > 0.179f) Color.BLACK else Color.WHITE) }
                    // Cosmetic metadata must never replace a search field or pending setup.
                    if (!activity.drawer && !activity.searchMode && activity.setupController.firstRunSetup == null &&
                        !activity.startupController.coreRecoveryVisible) activity.homeController.showHome()
                }
            }
        }
    }

    fun shutdown() {
        destroyed = true; generation++
        if (observing) runCatching { manager.removeOnColorsChangedListener(listener) }
        worker.shutdownNow()
    }
}

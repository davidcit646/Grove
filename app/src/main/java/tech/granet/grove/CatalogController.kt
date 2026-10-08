package tech.granet.grove

import android.content.*
import android.graphics.*
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import tech.granet.grove.ui.dp
import java.util.*

/** App snapshot and icon publication. Catalog failure closes to recovery; icons fall back; stale generations are discarded. */
internal class CatalogController(private val activity: MainActivity) {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun shutdown() { loadGeneration++; worker.shutdownNow() }
    internal var apps = emptyList<App>()
    internal var appSearch = SearchResults.prepare(apps) { it.searchName }
    @Volatile internal var loadGeneration = 0
    internal var state: CatalogState = CatalogState.Loading
    internal val iconCache get() = with(activity) { AppIconStore }
    internal val appCatalog by lazy { with(activity) { AppCatalog(launcher, packageManager, packageName, worker) } }
    internal var drawerVisibleCount = 0

    fun loadApps(changedPackage: String? = null) {
        with(activity) {
            if (isDestroyed || worker.isShutdown) return
            if (changedPackage != null && searchMode) searchController.refreshSettings()
            val generation = ++loadGeneration
            state = CatalogState.Loading
            val iconSize = dp(48)
            AppIconStore.useSize(iconSize)
            val reusable = iconCache.snapshotExcluding(changedPackage)
            appCatalog.load(
                changedPackage, iconSize, reusable,
                current = { generation == loadGeneration && !isDestroyed },
                onCatalog = { loadedApps, fallbackIcon ->
                    val preparedApps = SearchResults.prepare(loadedApps) { it.searchName }
                    runOnUiThread {
                        if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                        apps = loadedApps
                        appSearch = preparedApps
                        state = CatalogState.Ready(loadedApps.size)
                        drawerVisibleCount = if (loadedApps.all { reusable.containsKey(it.key) })
                            loadedApps.size else minOf(24, loadedApps.size)
                        iconCache.replace(loadedApps.associate { it.key to (reusable[it.key] ?: fallbackIcon) })
                        if (!prefs.contains("initialized")) {
                            val initial = if (!setupController.setupPending() && configController.config.favorites.isEmpty())
                                configController.config.copy(favorites = apps.take(8).map { it.key }) else configController.config
                            if (configController.commitConfig(initial) && !prefs.edit().putBoolean("initialized", true).commit())
                                Log.w("Grove", "Could not persist initialization marker")
                        }
                        if (startupController.coreRecoveryState?.reason == CoreRecoveryReason.APP_CATALOG) {
                            startupController.clearCoreRecovery()
                            root.setBackgroundColor(Color.TRANSPARENT)
                            homeController.showHome()
                        } else if (drawer) drawerController.renderApps(searchController.searchField?.text?.toString().orEmpty())
                        else if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
                        else if (!pinDragController.busy && !searchTutorialController.visible) homeController.showHome()
                        if (setupController.setupPending() && setupController.firstRunSetup == null &&
                            configController.configStore.brokenCustomConfig == null) root.post { if (!isDestroyed) setupController.startFirstRunSetup() }
                    }
                },
                onIcons = { batch -> publishIcons(generation, batch) },
                onComplete = { iconFailures ->
                    runOnUiThread {
                        if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                        state = if (iconFailures == 0) CatalogState.Ready(apps.size) else CatalogState.Degraded(apps.size, iconFailures)
                        drawerVisibleCount = apps.size
                        if (drawer && searchController.searchField?.text.isNullOrEmpty()) drawerController.renderApps("")
                    }
                },
                onFailure = { error ->
                    Log.w("Grove", "Unable to load apps", error)
                    runOnUiThread {
                        if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                        state = CatalogState.Failed
                        apps = emptyList()
                        appSearch = SearchResults.prepare(apps) { it.searchName }
                        iconCache.clear()
                        startupController.showCoreRecovery(CoreRecoveryReason.APP_CATALOG)
                    }
                },
            )
        }
    }

    fun publishIcons(generation: Int, batch: Map<String, Bitmap>) {
        with(activity) {
            runOnUiThread {
                if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                iconCache.putAll(batch)
                drawerVisibleCount = minOf(apps.size, drawerVisibleCount + 16)
                fun update(view: View) {
                    if (view is ImageView) (view.tag as? String)?.let { key ->
                        batch[key]?.let(view::setImageBitmap)
                    }
                    if (view is ViewGroup) for (index in 0 until view.childCount) update(view.getChildAt(index))
                }
                update(root)
                if (drawer && searchController.searchField?.text.isNullOrEmpty()) drawerController.renderApps("")
                else drawerController.drawerAdapter?.notifyDataSetChanged()
            }
        }
    }
}

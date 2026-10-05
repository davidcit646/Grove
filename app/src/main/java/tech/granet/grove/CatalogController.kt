package tech.granet.grove

import android.content.*
import android.graphics.*
import android.provider.Settings
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import tech.granet.grove.ui.dp
import java.util.*

/** CatalogController owns its lane; Android lifecycle and results remain in MainActivity. */
internal class CatalogController(private val activity: MainActivity) {
    internal var apps = emptyList<App>()
    internal var appSearch = SearchResults.prepare(apps) { it.searchName }
    @Volatile internal var loadGeneration = 0
    internal var loadingApps = true
    internal val iconCache get() = with(activity) { AppIconStore }
    internal val appCatalog by lazy { with(activity) { AppCatalog(launcher, packageManager, packageName, worker) } }
    internal var drawerVisibleCount = 0

    fun loadApps(changedPackage: String? = null) {
        with(activity) {
            if (isDestroyed || worker.isShutdown) return
            val generation = ++catalogController.loadGeneration
            val iconSize = dp(48)
            AppIconStore.useSize(iconSize)
            val reusable = catalogController.iconCache.snapshotExcluding(changedPackage)
            catalogController.appCatalog.load(
                changedPackage, iconSize, reusable,
                current = { generation == catalogController.loadGeneration && !isDestroyed },
                onCatalog = { loadedApps, fallbackIcon ->
                    val preparedApps = SearchResults.prepare(loadedApps) { it.searchName }
                    runOnUiThread {
                        if (isDestroyed || generation != catalogController.loadGeneration) return@runOnUiThread
                        catalogController.apps = loadedApps
                        catalogController.appSearch = preparedApps
                        catalogController.drawerVisibleCount = if (loadedApps.all { reusable.containsKey(it.key) })
                            loadedApps.size else minOf(24, loadedApps.size)
                        catalogController.iconCache.replace(loadedApps.associate { it.key to (reusable[it.key] ?: fallbackIcon) })
                        if (!prefs.contains("initialized")) {
                            val initial = if (!setupController.setupPending() && configController.config.favorites.isEmpty())
                                configController.config.copy(favorites = catalogController.apps.take(8).map { it.key }) else configController.config
                            if (configController.commitConfig(initial)) prefs.edit().putBoolean("initialized", true).apply()
                        }
                        if (startupController.coreRecoveryVisible) {
                            startupController.coreRecoveryVisible = false
                            root.setBackgroundColor(Color.TRANSPARENT)
                            homeController.showHome()
                        } else if (drawer) drawerController.renderApps(searchController.searchField?.text?.toString().orEmpty())
                        else if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
                        else if (!pinDragController.busy) homeController.showHome()
                        if (setupController.setupPending() && setupController.firstRunSetup == null &&
                            configController.configStore.brokenCustomConfig == null) root.post { if (!isDestroyed) setupController.startFirstRunSetup() }
                    }
                },
                onIcons = { batch -> catalogController.publishIcons(generation, batch) },
                onComplete = {
                    runOnUiThread {
                        if (isDestroyed || generation != catalogController.loadGeneration) return@runOnUiThread
                        catalogController.loadingApps = false
                        catalogController.drawerVisibleCount = catalogController.apps.size
                        if (drawer && searchController.searchField?.text.isNullOrEmpty()) drawerController.renderApps("")
                    }
                },
                onFailure = { error ->
                    Log.w("Grove", "Unable to load apps", error)
                    runOnUiThread {
                        if (isDestroyed || generation != catalogController.loadGeneration) return@runOnUiThread
                        catalogController.loadingApps = false
                        catalogController.apps = emptyList()
                        catalogController.appSearch = SearchResults.prepare(apps) { it.searchName }
                        catalogController.iconCache.clear()
                        startupController.showCoreRecovery("Android could not provide the installed app list. Retry, or change your Home app in Android Settings.")
                    }
                },
            )
        }
    }

    fun publishIcons(generation: Int, batch: Map<String, Bitmap>) {
        with(activity) {
            runOnUiThread {
                if (isDestroyed || generation != catalogController.loadGeneration) return@runOnUiThread
                catalogController.iconCache.putAll(batch)
                catalogController.drawerVisibleCount = minOf(catalogController.apps.size, catalogController.drawerVisibleCount + 16)
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

package tech.granet.grove

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.concurrent.ExecutorService

/** A launchable activity and its prepared search label. */
internal data class App(val component: ComponentName, val label: String, private val preparedName: String? = null) {
    val key = component.flattenToString()
    val searchName = preparedName ?: Search.normalize(label.take(512))
}

/** Explicit app-catalog outcome. Icon failure degrades visuals without removing launchable apps. */
internal sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val count: Int) : CatalogState
    data class Degraded(val count: Int, val iconFailures: Int) : CatalogState
    data object Failed : CatalogState
}

/**
 * Pure catalog orchestration. Android-specific enumeration/bitmap work is injected by AppCatalog,
 * which keeps generation cancellation and fallback behavior deterministic under unit test.
 */
internal class CatalogPipeline<E, I>(
    private val enumerate: () -> List<E>,
    private val key: (E) -> String,
    private val packageName: (E) -> String,
    private val fallback: () -> I,
    private val loadIcon: (E) -> I,
) {
    fun run(
        changedPackage: String?,
        reusable: Map<String, I>,
        current: () -> Boolean,
        onCatalog: (List<E>, I) -> Unit,
        onIcons: (Map<String, I>) -> Unit,
        onComplete: (Int) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        try {
            if (!current()) return
            val entries = enumerate()
            if (!current()) return
            val fallbackIcon = fallback()
            if (!current()) return
            onCatalog(entries, fallbackIcon)
            var iconFailures = 0
            val batch = LinkedHashMap<String, I>(16)
            for (entry in entries) {
                if (!current()) return
                val entryKey = key(entry)
                if (reusable.containsKey(entryKey) && packageName(entry) != changedPackage) continue
                val icon = try {
                    loadIcon(entry)
                } catch (_: Exception) {
                    iconFailures++
                    fallbackIcon
                }
                batch[entryKey] = icon
                if (batch.size >= 16) {
                    if (!current()) return
                    onIcons(LinkedHashMap(batch))
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                if (!current()) return
                onIcons(LinkedHashMap(batch))
            }
            if (current()) onComplete(iconFailures)
        } catch (error: Exception) {
            if (current()) onFailure(error)
        }
    }
}

/** Enumerates apps before decoding icons; failed icons never remove an app. */
internal class AppCatalog(
    private val launcher: LauncherApps,
    private val packageManager: PackageManager,
    private val ownPackage: String,
    private val worker: ExecutorService,
) {
    fun load(
        changedPackage: String?,
        iconSize: Int,
        reusable: Map<String, Bitmap>,
        current: () -> Boolean,
        onCatalog: (List<App>, Bitmap) -> Unit,
        onIcons: (Map<String, Bitmap>) -> Unit,
        onComplete: (Int) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        worker.execute {
            val pipeline = CatalogPipeline(
                enumerate = {
                    val entries = launcher.getActivityList(null, android.os.Process.myUserHandle())
                        .filter { it.componentName.packageName != ownPackage }

                    val labels = entries.map { it.label.toString().take(512) }
                    val normalized = Search.normalizeAll(labels, current)
                    entries.mapIndexed { index, item -> App(item.componentName, labels[index], normalized[index]) }
                        .sortedBy { it.searchName }
                },
                key = { app: App -> app.key },
                packageName = { app: App -> app.component.packageName },
                fallback = {
                    Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888).also { bitmap ->
                        packageManager.defaultActivityIcon.apply {
                            setBounds(0, 0, iconSize, iconSize)
                            draw(Canvas(bitmap))
                        }
                    }
                },
                loadIcon = { app: App ->
                    val drawable = packageManager.getActivityIcon(app.component)
                    Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888).also { bitmap ->
                        drawable.setBounds(0, 0, iconSize, iconSize)
                        drawable.draw(Canvas(bitmap))
                    }
                },
            )
            pipeline.run(changedPackage, reusable, current, onCatalog, onIcons, onComplete, onFailure)
        }
    }
}

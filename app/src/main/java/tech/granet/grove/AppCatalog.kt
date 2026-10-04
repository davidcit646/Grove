package tech.granet.grove

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import java.util.concurrent.ExecutorService

/** A launchable activity and its prepared search label. */
internal data class App(val component: ComponentName, val label: String) {
    val key = component.flattenToString()
    val searchName = Search.normalize(label.take(512))
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
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        worker.execute {
            try {
                val apps = launcher.getActivityList(null, android.os.Process.myUserHandle())
                    .filter { it.componentName.packageName != ownPackage }
                    .map { App(it.componentName, it.label.toString().take(512)) }
                    .sortedBy { it.searchName }
                if (!current()) return@execute
                val fallback = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                packageManager.defaultActivityIcon.apply {
                    setBounds(0, 0, iconSize, iconSize)
                    draw(Canvas(fallback))
                }
                onCatalog(apps, fallback)
                val batch = HashMap<String, Bitmap>(16)
                apps.forEach { app ->
                    if (!current()) return@execute
                    if (reusable.containsKey(app.key) &&
                        app.component.packageName != changedPackage) return@forEach
                    val icon = try {
                        val drawable = packageManager.getActivityIcon(app.component)
                        Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888).also { bitmap ->
                            drawable.setBounds(0, 0, iconSize, iconSize)
                            drawable.draw(Canvas(bitmap))
                        }
                    } catch (_: Exception) {
                        fallback
                    }
                    batch[app.key] = icon
                    if (batch.size >= 16) {
                        onIcons(HashMap(batch))
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onIcons(HashMap(batch))
                if (current()) onComplete()
            } catch (error: Exception) {
                if (current()) onFailure(error)
            }
        }
    }
}

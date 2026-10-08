package tech.granet.grove

import android.content.*
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import tech.granet.grove.ui.MenuRow
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.menuDialog
import tech.granet.grove.ui.message
import java.util.*
import java.io.File

/** External actions and uninstall queue. Adapters check prerequisites; cancel or launch failure stops the batch. */
internal class ActionController(private val activity: MainActivity) {
    fun shutdown() { contactActions.shutdown(); uninstallBatch.cancel() }
    internal val uninstallBatch = UninstallBatch()
    internal val fileActions by lazy { with(activity) { FileActions(this) { configController.config.search.files } } }
    internal val contactActions by lazy { with(activity) {
        ContactActions(this, { configController.config }, searchController::hasContactAccess,
            { packageName -> catalogController.apps.any { it.component.packageName == packageName } }, this@ActionController::showActionMenu)
    } }
    internal val searchActions by lazy { with(activity) { SearchActions(this, this@ActionController::showActionMenu) } }

    fun sharedFileUri(file: File): Uri = with(activity) { fileActions.shareUri(file)
    }

    fun contactMenu(contact: ContactIndex.Contact): Unit = with(activity) { contactActions.show(contact)
    }

    fun openFile(file: IndexedFile): Unit = with(activity) { fileActions.open(file)
    }

    fun openPlayStore(query: String, install: Boolean = false): Unit = with(activity) {
        searchActions.openPlayStore(query, install)
    }

    fun appMenu(app: App) {
        with(activity) {
            val pinned = app.key in configController.config.favorites
            showActionMenu(app.label, listOf(
                Triple(if (pinned) "Unpin from home" else "Pin to home", R.drawable.ic_grid) {
                    val next = configController.config.copy(favorites = if (pinned) configController.config.favorites - app.key else configController.config.favorites + app.key)
                    if (configController.commitConfig(next) && !drawer) homeController.showHome()
                },
                Triple("App info", R.drawable.ic_info) {
                    runCatching { launcher.startAppDetailsActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                        .onFailure { message("App information unavailable") }
                },
                Triple("Uninstall", R.drawable.ic_delete) {
                    runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))) }
                        .onFailure { message("This app cannot be uninstalled") }
                },
            ))
        }
    }

    fun searchItemMenu(file: IndexedFile) {
        with(activity) {
            showActionMenu(file.name, listOf(
                Triple("Open", R.drawable.ic_open) { openFile(file) },
                Triple("Open containing folder", R.drawable.ic_folder) {
                    val parent = file.file.parentFile
                    if (parent == null) message("Containing folder unavailable") else openFile(IndexedFile(parent.name, "resource/folder", parent, "Folder"))
                },
                Triple("Share", R.drawable.ic_share) {
                    runCatching {
                        val uri = sharedFileUri(file.file)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = file.mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share ${file.name}"))
                    }.onFailure { message("Could not share this file") }
                },
                Triple("File details", R.drawable.ic_info) {
                    infoDialog(file.name,
                        "${file.category}\n${file.file.absolutePath}\n${file.file.length()} bytes", "Done")
                },
            ))
        }
    }

    fun webResultMenu(query: String, provider: String): Unit = with(activity) { searchActions.webResultMenu(query, provider)
    }

    fun playStoreMenu(query: String): Unit = with(activity) { searchActions.playStoreMenu(query)
    }

    fun showActionMenu(title: String, actions: List<Triple<String, Int, () -> Unit>>) {
        with(activity) {
            menuDialog(title, actions.map { (name, icon, action) -> MenuRow(name, icon, action) })
        }
    }

    fun openWeb(url: String): Unit = with(activity) { searchActions.openWeb(url)
    }

    fun uninstallSelected(keys: Set<String>) {
        with(activity) {
            val packages = catalogController.apps.filter { it.key in keys }.map { it.component.packageName }.distinct()
            confirmDialog("Uninstall ${packages.size} app${if (packages.size == 1) "" else "s"}?",
                "Android will ask you to confirm each uninstall.", "Continue") {
                    launchNextUninstall(uninstallBatch.start(packages))
                    drawerController.drawerState.clearKeys(); drawerController.refreshDrawer()
                }
        }
    }

    fun launchNextUninstall(packageName: String?) {
        with(activity) {
            if (packageName == null) return
            try {
                uninstallNext.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true))
            } catch (error: Exception) {
                uninstallBatch.cancel()
                Log.w("Grove", "Could not launch batch uninstall for $packageName", error)
                message("Cannot uninstall $packageName")
            }
        }
    }
}

package tech.granet.grove

import android.content.*
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.os.*
import android.text.InputFilter
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message
import java.util.*

/** Active configuration and document/editor flows. Persistence must succeed before publication or completion. */
internal class ConfigController(private val activity: MainActivity) {
    private val documents = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var documentGeneration = 0
    private val app get() = activity.application as GroveApp
    internal val repository get() = app.settingsRepository
    internal val config get() = repository.snapshot().config
    internal val configStore get() = app.settingsStore
    internal val workflow by lazy { ConfigWorkflow({ config }, ::activateConfig) }
    private var reconciled: Config? = null

    fun load(): Config = repository.snapshot().config

    fun resume() {
        val next = config
        val previous = reconciled
        if (previous != next) {
            reconciled = next
            if (previous != null) {
                activity.searchController.reconcileAccess()
                if (!activity.drawer && !activity.searchMode && activity.setupController.firstRunSetup == null &&
                    !activity.startupController.coreRecoveryVisible) activity.homeController.showHome()
                else activity.drawerController.refreshDrawer()
            }
            activity.presentationController.applyTheme(next.themeMode)
        }
    }

    fun commitConfig(next: Config): Boolean = commit(next, false)
    fun activateConfig(next: Config): Boolean = commit(next, true)
    private fun commit(next: Config, replacement: Boolean): Boolean {
        val previous = config
        return when (val result = repository.update(replacement = replacement) { next }) {
            is SettingsOutcome.Saved -> {
                reconciled = result.snapshot.config
                // Persisted state stays saved even if a feature effect degrades afterward.
                try {
                    if (previous.search != next.search) activity.searchController.applySearchSettings(previous.search)
                    activity.presentationController.applyTheme(next.themeMode)
                } catch (error: Exception) {
                    Log.e("Grove", "Saved settings; feature refresh unavailable", error)
                    activity.message("Settings saved; return Home to refresh")
                }
                true
            }
            is SettingsOutcome.Invalid -> { activity.message(result.reason); false }
            else -> { GroveErrorPresenter.show(activity, GroveErrorRegistry.CONFIG_PERSIST); false }
        }
    }

    fun editConfig(initialText: String? = null) {
        activity.startActivity(Intent(activity, SettingsActivity::class.java).putExtra("route", "editor")
            .putExtra("draft", initialText ?: config.json()))
    }
    fun showConfigRecoveryDialog() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java).putExtra("route", "recovery"))
    }
    fun exportDocument(uri: Uri) {
        val snapshot = config
        val generation = ++documentGeneration
        documents.execute {
            val result = runCatching {
                activity.contentResolver.openOutputStream(uri)?.use { ConfigDocuments.write(snapshot, it) }
                    ?: error("Cannot open file")
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || generation != documentGeneration) return@runOnUiThread
                result.onSuccess { activity.message("Configuration exported") }.onFailure {
                    GroveErrorPresenter.show(activity, GroveErrorRegistry.CONFIG_EXPORT) { activity.export.launch("grove-config.json") }
                }
            }
        }
    }

    fun importDocument(uri: Uri) {
        activity.startActivity(Intent(activity, SettingsActivity::class.java).putExtra("importUri", uri.toString()))
    }

    fun shutdown() { documentGeneration++; documents.shutdownNow() }
}

internal object ConfigDocumentGate {
    fun canActivate(startedWith: Config, current: Config): Boolean = startedWith == current
}

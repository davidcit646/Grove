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
    internal var config = Config()
    internal val configStore by lazy { with(activity) { ConfigStore(activity.prefs) } }
    internal val workflow by lazy { ConfigWorkflow({ config }, ::activateConfig) }

    fun commitConfig(next: Config): Boolean {
        with(activity) {
            return ConfigTransaction.commit(next, configStore::save, { config = it }) { error ->
                Log.e("Grove", "Could not save settings", error)
                GroveErrorPresenter.show(this, GroveErrorRegistry.CONFIG_PERSIST)
            }
        }
    }

    fun activateConfig(next: Config): Boolean {
        with(activity) {
            val previousSearch = config.search
            val committed = ConfigTransaction.commit(next, configStore::activate, { config = it }) { error ->
                Log.e("Grove", "Could not activate settings", error)
                GroveErrorPresenter.show(this, GroveErrorRegistry.CONFIG_PERSIST)
            }
            if (committed && previousSearch != next.search) searchController.applySearchSettings(previousSearch)
            return committed
        }
    }

    fun editConfig(initialText: String? = null) {
        with(activity) {
            val editor = EditText(this).apply {
                filters = arrayOf(InputFilter.LengthFilter(65_536))
                setText(initialText ?: config.json())
                typeface = Typeface.MONOSPACE
                minLines = 8
            }
            val dialog = MaterialAlertDialogBuilder(this).setTitle("Configuration").setView(editor).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
            dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
                runCatching { require(editor.length() <= 65536); workflow.replaceBroken(editor.text.toString()) }
                    .onSuccess { activated ->
                        if (activated) { dialog.dismiss(); homeController.showHome() }
                        else editor.error = "Could not save configuration"
                    }.onFailure { editor.error = it.message ?: "Invalid JSON" }
            } }; dialog.show()
        }
    }

    fun showConfigRecoveryDialog() {
        with(activity) {
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle("Configuration problem")
                .setMessage("Grove couldn’t read your saved custom configuration, so a safe fallback configuration is active. Your custom configuration has been preserved. You can continue editing it and try loading it, or load defaults and start over.")
                .setCancelable(false)
                .setPositiveButton("Edit custom config") { _, _ -> editConfig(configStore.brokenCustomConfig) }
                .setNegativeButton("Load defaults", null)
                .create()
            // A normal dialog button dismisses even when persistence fails. Keep
            // recovery available until the replacement config actually commits.
            dialog.setOnShowListener {
                dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    if (workflow.replaceWithDefaults()) {
                        dialog.dismiss()
                        homeController.showHome()
                        message("Default configuration loaded")
                    }
                }
            }
            dialog.show()
        }
    }
    fun exportDocument(uri: Uri) = with(activity) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use(workflow::export)
                ?: error("Cannot open file")
        }.onFailure {
            GroveErrorPresenter.show(this, GroveErrorRegistry.CONFIG_EXPORT) { export.launch("grove-config.json") }
        }
        Unit
    }

    fun importDocument(uri: Uri) = with(activity) {
        runCatching {
            contentResolver.openInputStream(uri)?.use(workflow::import)
                ?: error("Cannot open file")
        }.onSuccess { activated ->
            if (activated) {
                homeController.showHome()
                message("Configuration imported")
            }
        }.onFailure {
            GroveErrorPresenter.show(this, GroveErrorRegistry.CONFIG_IMPORT) {
                importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        Unit
    }
}

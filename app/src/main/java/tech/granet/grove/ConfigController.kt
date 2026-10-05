package tech.granet.grove

import android.content.*
import android.net.Uri
import android.graphics.*
import android.util.Log
import android.os.*
import android.text.InputFilter
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message
import java.util.*

/** ConfigController owns its lane; Android lifecycle and results remain in MainActivity. */
internal class ConfigController(private val activity: MainActivity) {
    internal var config = Config()
    internal val configStore by lazy { with(activity) { ConfigStore(activity.prefs) } }

    fun commitConfig(next: Config): Boolean {
        with(activity) {
            return ConfigTransaction.commit(next, configController.configStore::save, { configController.config = it }) { error ->
                Log.e("Grove", "Could not save settings", error)
                message("Could not save Grove settings")
            }
        }
    }

    fun activateConfig(next: Config): Boolean {
        with(activity) {
            val previousSearch = configController.config.search
            val committed = ConfigTransaction.commit(next, configController.configStore::activate, { configController.config = it }) { error ->
                Log.e("Grove", "Could not activate settings", error)
                message("Could not save Grove settings")
            }
            if (committed && previousSearch != next.search) searchController.applySearchSettings(previousSearch)
            return committed
        }
    }

    fun editConfig(initialText: String? = null) {
        with(activity) {
            val editor = EditText(this).apply {
                filters = arrayOf(InputFilter.LengthFilter(65_536))
                setText(initialText ?: configController.config.json())
                typeface = Typeface.MONOSPACE
                minLines = 8
            }
            val dialog = MaterialAlertDialogBuilder(this).setTitle("Configuration").setView(editor).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
            dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
                runCatching { require(editor.length() <= 65536); ConfigStore.parse(editor.text.toString()) }
                    .onSuccess { if (configController.activateConfig(it)) { dialog.dismiss(); homeController.showHome() } else editor.error = "Could not save configuration" }.onFailure { editor.error = it.message ?: "Invalid JSON" }
            } }; dialog.show()
        }
    }

    fun showConfigRecoveryDialog() {
        with(activity) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Configuration problem")
                .setMessage("Grove couldn’t read your saved custom configuration, so a safe fallback configuration is active. Your custom configuration has been preserved. You can continue editing it and try loading it, or load defaults and start over.")
                .setCancelable(false)
                .setPositiveButton("Edit custom config") { _, _ -> configController.editConfig(configController.configStore.brokenCustomConfig) }
                .setNegativeButton("Load defaults") { _, _ -> if (configController.activateConfig(Config())) { homeController.showHome(); message("Default configuration loaded") } }
                .show()
        }
    }
    fun exportDocument(uri: Uri) = with(activity) {
        runCatching {
            contentResolver.openOutputStream(uri)?.use { ConfigDocuments.write(config, it) }
                ?: error("Cannot open file")
        }.onFailure { message("Could not export configuration") }
        Unit
    }

    fun importDocument(uri: Uri) = with(activity) {
        runCatching {
            contentResolver.openInputStream(uri)?.use(ConfigDocuments::read)
                ?: error("Cannot open file")
        }.onSuccess {
            if (activateConfig(it)) {
                homeController.showHome()
                message("Configuration imported")
            }
        }.onFailure { message(it.message ?: "Invalid configuration") }
        Unit
    }
}

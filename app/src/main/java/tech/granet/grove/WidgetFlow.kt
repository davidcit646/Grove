package tech.granet.grove

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.listDialog

/** Widget setup owns allocate/bind/configure/commit; Activity owns result launchers. */
internal class WidgetFlow(
    private val activity: AppCompatActivity,
    private val manager: AppWidgetManager,
    private val host: AppWidgetHost,
    private val prefs: SharedPreferences,
    private val widgets: WidgetRegistry,
    private val launchBind: (Intent) -> Unit,
    private val launchConfigure: (Intent) -> Unit,
    private val refresh: () -> Unit,
    private val message: (String) -> Unit,
) {
    // Widget lifecycle: allocate -> bind consent -> optional configuration -> persist.
    fun pick() {
        if (widgets.pending != -1) {
            MaterialAlertDialogBuilder(activity).setTitle("Widget setup interrupted")
                .setMessage("Retry saving the pending widget or remove it before adding another.")
                .setPositiveButton("Retry") { _, _ -> configure() }
                .setNegativeButton("Remove") { _, _ -> cancel() }
                .show()
            return
        }
        if (!prefs.getBoolean("widget_tutorial_seen", false)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Widget controls")
                .setMessage("Once a widget is on your Home screen, tap and hold it for options such as resizing, configuring, or removing it.")
                .setPositiveButton("Got It!") { _, _ ->
                    prefs.edit().putBoolean("widget_tutorial_seen", true).apply()
                    showPicker()
                }
                .setNegativeButton("Not now", null)
                .show()
            return
        }
        showPicker()
    }
    private fun showPicker() {
        val providers = runCatching {
            manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }
        }.onFailure { Log.w("Grove", "Widget providers unavailable", it) }.getOrNull()
        if (providers.isNullOrEmpty()) { message("No widgets available"); return }
        activity.listDialog("Add widget", providers.map { it.loadLabel(activity.packageManager).toString() }, negative = "Cancel") { index ->
            val provider = providers.getOrNull(index) ?: return@listDialog
            val id = runCatching { widgets.allocate() }
                .onFailure { Log.e("Grove", "Widget allocation failed", it) }.getOrNull()
                ?: run { message("Cannot allocate widget"); return@listDialog }
            val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.provider) }
                .onFailure { Log.e("Grove", "Widget bind failed", it) }.getOrNull()
            if (bound == true) configure()
            else if (bound == false) runCatching {
                launchBind(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider))
            }.onFailure { Log.e("Grove", "Widget bind launch failed", it); cancel(); message("Cannot bind this widget") }
            else { cancel(); message("Cannot bind this widget") }
        }
    }

    fun configure() {
        val id = widgets.pending
        val info = runCatching { manager.getAppWidgetInfo(id) }
            .onFailure { Log.w("Grove", "Widget provider lookup failed for $id", it) }.getOrNull() ?: run {
            Log.w("Grove", "No widget provider info for id $id")
            cancel(); message("Couldn't add this widget"); return
        }
        if (info.configure != null) {
            runCatching { launchConfigure(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) }
                .onFailure { Log.e("Grove", "Widget configuration failed to launch for ${info.configure}", it); cancel(); message("Widget configuration unavailable") }
        } else finish()
    }

    fun finish() {
        if (widgets.finish()) refresh()
        else message("Couldn't save this widget; retry or remove it")
    }

    fun cancel() {
        if (!widgets.cancel()) message("Couldn't release widget; retry")
    }

    fun render(target: LinearLayout) =
        WidgetScreen(activity, manager, host, prefs, widgets.ids, widgets::remove) { refresh() }.render(target)

}

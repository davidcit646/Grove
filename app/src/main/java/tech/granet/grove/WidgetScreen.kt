package tech.granet.grove

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.label
import tech.granet.grove.ui.listDialog

/** Displays provider-owned RemoteViews. Grove only manages size and removal. */
internal class WidgetScreen(
    private val activity: AppCompatActivity,
    private val manager: AppWidgetManager,
    private val host: AppWidgetHost,
    private val prefs: SharedPreferences,
    private val ids: MutableList<Int>,
    private val changed: () -> Unit,
) {

    fun render(body: LinearLayout) {
        ids.toList().forEach { id ->
            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                Log.w("Grove", "Widget $id no longer has provider info")
                body.addView(activity.label("This widget is unavailable. Long-press to remove it.", 14f, 12).apply {
                    setOnLongClickListener {
                        host.deleteAppWidgetId(id)
                        ids.remove(id)
                        prefs.edit().putStringSet("widgets", ids.map { it.toString() }.toSet())
                            .remove("height_$id").apply()
                        changed(); true
                    }
                })
                return@forEach
            }
            try {
                val widget = host.createView(activity.applicationContext, id, info)
                val minHeight = (info.minHeight / activity.resources.displayMetrics.density).toInt()
                val height = maxOf(prefs.getInt("height_$id", minHeight.coerceAtLeast(120)), minHeight)
                body.addView(widget, LinearLayout.LayoutParams(-1, activity.dp(height)))
                // Providers often size their RemoteViews from the options bundle (in
                // particular responsive and collection widgets). A host view's measured
                // bounds alone are not enough: notify every provider after layout, not
                // only widgets whose height was manually changed.
                widget.post {
                    if (widget.width > 0) {
                        val widthDp = (widget.width / activity.resources.displayMetrics.density).toInt()
                        val heightDp = (widget.height / activity.resources.displayMetrics.density).toInt()
                        runCatching {
                            widget.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
                        }.onFailure {
                            Log.w("Grove", "Widget size notification failed for ${info.provider} (id=$id, ${widthDp}x${heightDp}dp)", it)
                        }
                    }
                }
                installLongPress(widget, info, id)
            } catch (error: Exception) {
                Log.e("Grove", "Failed to create widget ${info.provider.flattenToShortString()} (id=$id)", error)
                body.addView(activity.label("This widget could not be displayed. Long-press to remove it.", 14f, 12).apply {
                    setOnLongClickListener { showMenu(id, info); true }
                })
            }
        }
    }

    private fun installLongPress(view: View, info: AppWidgetProviderInfo, id: Int) {
        view.isLongClickable = true
        view.setOnLongClickListener { showMenu(id, info); true }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) installLongPress(view.getChildAt(index), info, id)
        }
    }

    private fun showMenu(id: Int, info: AppWidgetProviderInfo) {
        val actions = buildList {
            if (info.configure != null) add("Configure widget")
            add("Compact height")
            add("Medium height")
            add("Tall height")
            add("Remove widget")
        }
        activity.listDialog(info.loadLabel(activity.packageManager).toString(), actions) { which ->
            when (actions[which]) {
                "Configure widget" -> configure(id, info)
                "Compact height" -> resize(id, info, 140)
                "Medium height" -> resize(id, info, 240)
                "Tall height" -> resize(id, info, 360)
                "Remove widget" -> {
                    host.deleteAppWidgetId(id)
                    ids.remove(id)
                    prefs.edit().putStringSet("widgets", ids.map { it.toString() }.toSet())
                        .remove("height_$id").apply()
                    changed()
                }
            }
        }
    }

    private fun resize(id: Int, info: AppWidgetProviderInfo, requested: Int) {
        val min = (info.minHeight / activity.resources.displayMetrics.density).toInt()
        prefs.edit().putInt("height_$id", maxOf(requested, min)).apply()
        changed()
    }

    private fun configure(id: Int, info: AppWidgetProviderInfo) {
        val component = info.configure ?: return
        runCatching {
            activity.startActivity(android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }.onFailure {
            Log.e("Grove", "Could not launch widget configuration for ${component.flattenToShortString()}", it)
            activity.infoDialog("Widget settings unavailable", "This widget's settings screen could not be opened.")
        }
    }
}

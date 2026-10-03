package tech.granet.grove

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log

/** Owns widget IDs and persists each lifecycle transition before exposing it to Home. */
internal class WidgetRegistry(
    private val host: AppWidgetHost,
    private val manager: AppWidgetManager,
    private val prefs: SharedPreferences,
) {
    private val stored = mutableListOf<Int>()
    val ids: List<Int> get() = stored.toList()
    var pending = -1
        private set

    fun restore(state: Bundle?) {
        stored.clear()
        stored.addAll(prefs.getStringSet("widgets", emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }.distinct().sorted())
        pending = state?.getInt("pending", -1) ?: prefs.getInt("pending", -1)
        if (state == null && pending != -1) cancel()
    }

    fun allocate(): Int {
        check(pending == -1) { "A widget is already pending" }
        val id = host.allocateAppWidgetId()
        if (!prefs.edit().putInt("pending", id).commit()) {
            host.deleteAppWidgetId(id)
            error("Could not persist pending widget")
        }
        pending = id
        return id
    }

    fun finish(): Boolean {
        val id = pending
        if (id == -1) return false
        if (manager.getAppWidgetInfo(id) == null) {
            cancel()
            return false
        }
        val next = (stored + id).distinct()
        if (!prefs.edit().putStringSet("widgets", next.map(Int::toString).toSet())
                .remove("pending").commit()) return false
        stored.clear()
        stored.addAll(next)
        pending = -1
        return true
    }

    fun cancel(): Boolean {
        val id = pending
        if (id == -1) return true
        if (!runCatching { host.deleteAppWidgetId(id) }
                .onFailure { Log.w("Grove", "Could not release pending widget $id", it) }.isSuccess) return false
        if (!prefs.edit().remove("pending").commit()) return false
        pending = -1
        return true
    }

    fun remove(id: Int): Boolean {
        if (id !in stored) return false
        if (!runCatching { host.deleteAppWidgetId(id) }
                .onFailure { Log.w("Grove", "Could not remove widget $id", it) }.isSuccess) return false
        val next = stored.filterNot { it == id }
        if (!prefs.edit().putStringSet("widgets", next.map(Int::toString).toSet())
                .remove("height_$id").commit()) return false
        stored.clear()
        stored.addAll(next)
        return true
    }
}

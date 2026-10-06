package tech.granet.grove

import android.os.Bundle
import android.os.Environment
import android.util.Log
import tech.granet.grove.ui.message

/** Entry/completion owner. Presentation receives examples and availability, never providers or writes. */
internal class SearchTutorialController(private val activity: MainActivity) {
    private var state = SearchTutorialState()
    private var presentation: SearchTutorial? = null
    private var restoreActive = false
    private var suppressedRequest = 0L
    val visible get() = presentation != null
    fun restore(saved: Bundle?) {
        saved?.getBundle("searchTutorial")?.let {
            state = SearchTutorialState(it.getInt("page"), it.getBoolean("suppressed"))
            restoreActive = it.getBoolean("active")
            suppressedRequest = it.getLong("suppressedRequest")
        }
    }
    fun save(out: Bundle) {
        out.putBundle("searchTutorial", Bundle().apply {
            putInt("page", state.page); putBoolean("suppressed", state.sessionSuppressed)
            putBoolean("active", visible || restoreActive); putLong("suppressedRequest", suppressedRequest)
        })
    }
    fun restoreEntry() {
        if (!restoreActive || activity.isDestroyed || activity.startupController.coreRecoveryVisible ||
            activity.setupController.firstRunSetup != null || activity.setupController.setupPending()) return
        restoreActive = false
        show()
    }
    fun interceptEntry(): Boolean {
        if (visible) return true
        if (activity.setupController.firstRunSetup != null) return true
        val shouldPresent = try {
            val request = activity.prefs.getLong("search_tutorial_request", 0L)
            if (request != suppressedRequest) state.sessionSuppressed = false
            SearchTutorialState.shouldShow(activity.prefs.getInt("search_tutorial_version", 0),
                activity.prefs.getBoolean("search_tutorial_pending", false), state.sessionSuppressed)
        } catch (error: Exception) {
            Log.w("Grove", "Search tutorial state unavailable", error)
            state.sessionSuppressed = true
            activity.message("Search tutorial unavailable; Search is still available.")
            false
        }
        if (!shouldPresent) return false
        state = SearchTutorialState(sessionSuppressed = state.sessionSuppressed)
        show()
        return true
    }
    private fun show() {
        if (visible) return
        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(activity.root.windowToken, 0)
        val settings = activity.configController.config.search
        val availability = SearchTutorialAvailability(settings.calculator, settings.contacts,
            activity.searchController.hasContactAccess(), settings.files, Environment.isExternalStorageManager(),
            settings.groveSettings, settings.androidSettings)
        presentation = SearchTutorial(activity, activity.surface, activity.root, state, availability,
            ::forward, ::back).also { it.show() }
    }
    private fun forward() {
        if (!state.forward()) { presentation?.page(true); return }
        suppressedRequest = try { activity.prefs.getLong("search_tutorial_request", 0L) }
            catch (_: Exception) { 0L }
        val saved = state.completed {
            try { activity.prefs.edit().putInt("search_tutorial_version", SearchTutorialState.VERSION)
                .remove("search_tutorial_pending").commit() }
            catch (error: Exception) { Log.w("Grove", "Search tutorial completion unavailable", error); false }
        }
        destroy()
        if (!saved) activity.message("Could not save tutorial completion; Search is still available.")
        activity.searchController.showSearch(skipTutorial = true)
    }
    fun back() {
        if (presentation?.busy == true) return
        if (state.back()) presentation?.page(false) else destroy()
    }
    fun stop() { presentation?.settle() }
    fun destroy() { presentation?.destroy(); presentation = null; restoreActive = false }
}

internal data class SearchTutorialAvailability(val calculator: Boolean, val contacts: Boolean,
    val contactAccess: Boolean, val files: Boolean, val fileAccess: Boolean,
    val groveSettings: Boolean, val androidSettings: Boolean)

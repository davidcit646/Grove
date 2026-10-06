package tech.granet.grove

import android.os.Bundle
import android.os.Environment
import android.util.Log

/** Entry/completion owner. Presentation receives examples and availability, never providers or writes. */
internal class SearchTutorialController(private val activity: MainActivity) {
    private val session = SearchTutorialSession()
    private var state: SearchTutorialState
        get() = session.state
        set(value) { session.state = value }
    private var presentation: SearchTutorial? = null
    private var restoreActive = false
    val visible get() = presentation != null
    fun restore(saved: Bundle?) {
        saved?.getBundle("searchTutorial")?.let {
            state = SearchTutorialState(it.getInt("page"), it.getBoolean("suppressed"))
            restoreActive = it.getBoolean("active")
            session.request = it.getLong("suppressedRequest")
        }
    }
    fun save(out: Bundle) {
        out.putBundle("searchTutorial", Bundle().apply {
            putInt("page", state.page); putBoolean("suppressed", state.sessionSuppressed)
            putBoolean("active", visible || restoreActive); putLong("suppressedRequest", session.request)
        })
    }
    fun restoreEntry() {
        if (!restoreActive || activity.isDestroyed || activity.startupController.coreRecoveryVisible ||
            activity.setupController.firstRunSetup != null || activity.setupController.setupPending()) return
        restoreActive = false
        if (!show()) activity.searchController.showSearch(skipTutorial = true)
    }
    fun interceptEntry(): Boolean {
        if (visible) return true
        if (activity.setupController.firstRunSetup != null) return true
        val shouldPresent = try {
            session.shouldPresent(activity.prefs.getInt("search_tutorial_version", 0),
                activity.prefs.getBoolean("search_tutorial_pending", false),
                activity.prefs.getLong("search_tutorial_request", 0L))
        } catch (error: Exception) {
            fail(GroveErrorRegistry.SEARCH_TUTORIAL_STATE, error)
            false
        }
        if (!shouldPresent) return false
        state = SearchTutorialState(sessionSuppressed = state.sessionSuppressed)
        return show()
    }
    private fun availability(): SearchTutorialAvailability {
        val settings = activity.configController.config.search
        return SearchTutorialAvailability(settings.calculator, settings.contacts,
            activity.searchController.hasContactAccess(), settings.files, Environment.isExternalStorageManager(),
            settings.groveSettings, settings.androidSettings)
    }
    private fun show(): Boolean {
        if (visible) return true
        return session.present(
            prepareUnderlay = activity.homeController::resetSwipeFeedback,
            render = {
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .hideSoftInputFromWindow(activity.root.windowToken, 0)
                presentation = SearchTutorial(activity, activity.surface, activity.root, state, availability(), ::forward, ::back)
                presentation?.show()
            },
            recover = { fail(GroveErrorRegistry.SEARCH_TUTORIAL_PRESENTATION, it) },
        )
    }
    private fun fail(error: GroveError, cause: Exception? = null) {
        session.suppress()
        destroy()
        Log.w("Grove", error.feature + " unavailable", cause)
        GroveErrorPresenter.show(activity, error)
    }
    private fun page(forward: Boolean) {
        try { presentation?.page(forward) }
        catch (error: Exception) {
            fail(GroveErrorRegistry.SEARCH_TUTORIAL_PRESENTATION, error)
            activity.searchController.showSearch(skipTutorial = true)
        }
    }
    fun refresh() {
        if (!visible) return
        try { presentation?.refresh(availability()) }
        catch (error: Exception) {
            fail(GroveErrorRegistry.SEARCH_TUTORIAL_PRESENTATION, error)
            activity.searchController.showSearch(skipTutorial = true)
        }
    }
    private fun forward() {
        if (!state.forward()) { page(true); return }
        val saved = state.completed {
            try { activity.prefs.edit().putInt("search_tutorial_version", SearchTutorialState.VERSION)
                .remove("search_tutorial_pending").commit() }
            catch (error: Exception) { Log.w("Grove", "Search tutorial completion unavailable", error); false }
        }
        destroy()
        if (!saved) fail(GroveErrorRegistry.SEARCH_TUTORIAL_COMPLETION)
        activity.searchController.showSearch(skipTutorial = true)
    }
    fun back() {
        if (presentation?.busy == true) return
        if (state.back()) page(false) else destroy()
    }
    fun stop() { presentation?.settle() }
    fun destroy() { presentation?.destroy(); presentation = null; restoreActive = false }
}

internal data class SearchTutorialAvailability(val calculator: Boolean, val contacts: Boolean,
    val contactAccess: Boolean, val files: Boolean, val fileAccess: Boolean,
    val groveSettings: Boolean, val androidSettings: Boolean)

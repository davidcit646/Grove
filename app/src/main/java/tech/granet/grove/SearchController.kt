package tech.granet.grove

import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors
import tech.granet.grove.ui.message
import tech.granet.grove.ui.wallpaperLabel

/**
 * Search-session coordinator. Android access, settings discovery and bounded live
 * source queries are delegated to dedicated owners; this class owns query
 * generation, debounce, result composition and Search-screen publication.
 */
internal class SearchController(private val activity: MainActivity) {
    internal val searchWorker = Executors.newSingleThreadExecutor()
    internal val searchHandler = Handler(Looper.getMainLooper())
    @Volatile internal var searchGeneration = 0
    internal var pendingSearch: Runnable? = null
    internal var searchResults: LinearLayout? = null
    internal var searchField: EditText? = null

    private val access by lazy { SearchAccessController(activity) { sources.reconcile() } }
    private val settings by lazy {
        SearchSettingsCoordinator(
            activity = activity,
            worker = searchWorker,
            currentQuery = { searchField?.text?.toString().orEmpty() },
            render = ::renderSearch,
        )
    }
    private val live by lazy {
        SearchLiveQueries(
            activity = activity,
            sources = { sources },
            currentGeneration = { searchGeneration },
            hasContacts = access::hasContacts,
            hasFiles = access::hasFiles,
            onChanged = ::refreshLiveDisplay,
        )
    }

    internal val sources by lazy {
        with(activity) {
            SearchSources(
                this,
                worker,
                contactWorker,
                { configController.config.search },
                access::hasContacts,
            ) {
                if (searchMode) refreshSources()
            }
        }
    }

    internal val contacts get() = sources.contacts
    internal val contactSearch get() = sources.contactSearch
    internal val files get() = sources.files
    internal val fileSearch get() = sources.fileSearch
    internal val indexingContacts get() = sources.indexingContacts
    internal val contactLoadFailed get() = sources.contactLoadFailed
    internal val lastContactRefresh get() = sources.lastContactRefresh
    internal val indexingFiles get() = sources.indexingFiles
    internal val fileLoadFailed get() = sources.fileLoadFailed
    internal val fileScanSkipped get() = sources.fileScanSkipped
    internal val searchScreen by lazy { SearchScreen(activity) }

    private var lastApps = emptyList<App>()
    private var lastContacts = emptyList<ContactIndex.Contact>()
    private var lastFiles = emptyList<IndexedFile>()
    private var sourceSnapshot: List<Any?> = emptyList()

    private fun sourceKey(): List<Any?> = listOf(
        sources.contactSearch,
        sources.fileSearch,
        sources.contactCacheReady,
        sources.fileCacheReady,
        sources.contactLoadFailed,
        sources.fileLoadFailed,
        sources.contactScanSkipped,
        sources.fileScanSkipped,
        (activity.application as GroveApp).contactChanges.changes.value,
    )

    private fun refreshSources() {
        val query = searchField?.text?.toString().orEmpty()
        if (sourceSnapshot != sourceKey()) renderSearch(query)
        else if (pendingSearch == null) refreshLiveDisplay(query)
    }

    internal fun refreshSettings() = settings.refresh()

    fun cancelPending() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        searchGeneration++
        live.cancel()
    }

    fun shutdown() {
        live.shutdown()
        searchWorker.shutdownNow()
    }

    fun reconcileAccess() {
        refreshSettings()
        val query = searchField?.text?.toString().orEmpty()
        cancelPending()
        sources.reconcile()
        if (activity.searchMode) {
            displaySearch(searchResults ?: return, query, lastApps, emptyList(), emptyList())
            renderSearch(query)
        }
    }

    fun indexFiles() {
        sources.indexFiles()
    }

    fun refreshContacts() {
        sources.refreshContacts(IndexRefreshCause.MANUAL)
    }

    fun hasContactAccess(): Boolean = access.hasContacts()
    fun requestContactAccess() = access.requestContacts()
    fun explainContactAccess() = access.explainContacts()
    fun requestFileAccess() = access.requestFiles()
    fun explainFileAccess() = access.explainFiles()

    fun applySearchSettings(previous: SearchSettings) {
        val query = searchField?.text?.toString().orEmpty()
        cancelPending()
        val current = activity.configController.config.search
        if (SearchSettingsEffects.protectedSources(previous, current)) sources.reconcile()
        if (previous.androidSettings != current.androidSettings) refreshSettings()
        if (current.contacts &&
            (!previous.contacts || (current.contactIndexing && !previous.contactIndexing)) &&
            !access.hasContacts()
        ) {
            access.explainContacts()
        }
        if (current.files &&
            (!previous.files || (current.fileIndexing && !previous.fileIndexing)) &&
            !access.hasFiles()
        ) {
            access.explainFiles()
        }
        if (activity.searchMode) {
            displaySearch(searchResults ?: return, query, lastApps, emptyList(), emptyList())
            renderSearch(query)
        }
    }

    fun showSearch(animate: Boolean = false, skipTutorial: Boolean = false) = with(activity) {
        if (isDestroyed || startupController.coreRecoveryVisible) return@with
        if (!skipTutorial && searchTutorialController.interceptEntry()) return@with
        homeController.rememberHomeScroll()
        drawerController.clearAppSelection()
        drawer = false
        searchMode = true
        homeController.base(readableBackdrop = true)
        root.addView(wallpaperLabel("Search", 30f))
        val field = EditText(this).apply {
            hint = getString(R.string.settings_search_hint)
            filters = arrayOf(InputFilter.LengthFilter(256))
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(0xffc1ccc5.toInt())
            contentDescription = getString(R.string.settings_search_description)
        }
        searchField = field
        root.addView(field)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchResults = list
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(homeController.button("Home") { homeController.animateDrawerClosed() })
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSearch(s.toString())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refreshSettings()
        renderSearch("")
        if (animate) homeController.enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
        field.requestFocus()
        field.post {
            if (searchMode && searchField === field) {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0)
            }
        }
    }

    fun renderSearch(query: String) {
        val target = searchResults ?: return
        val generation = ++searchGeneration
        live.cancel()
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null

        val prepared = Search.prepare(query)
        if (prepared.text.isEmpty()) {
            displaySearch(target, query, emptyList(), emptyList(), emptyList())
            return
        }

        val appSnapshot = activity.catalogController.appSearch
        val contactSnapshot = contactSearch
        val fileSnapshot = fileSearch
        val config = activity.configController.config.search
        val contactLive = config.contacts &&
            (!config.contactIndexing || !sources.contactCacheReady || sources.contactLoadFailed) &&
            access.hasContacts()
        val fileLive = config.files &&
            (!config.fileIndexing || !sources.fileCacheReady || sources.fileLoadFailed) &&
            access.hasFiles()
        live.begin(contactLive, fileLive)
        sourceSnapshot = sourceKey()

        val task = Runnable {
            if (searchWorker.isShutdown) return@Runnable
            searchWorker.execute {
                if (generation != searchGeneration) return@execute
                val settingsWork = settings.prepare(query, config)
                val matchingApps = SearchResults.matching(appSnapshot, prepared, 12)
                if (generation != searchGeneration) return@execute
                val matchingContacts = if (config.contacts && config.contactIndexing && access.hasContacts()) {
                    SearchResults.matching(contactSnapshot, prepared, 12)
                } else emptyList()
                if (generation != searchGeneration) return@execute
                val matchingFiles = if (config.files && config.fileIndexing && access.hasFiles()) {
                    SearchResults.matching(fileSnapshot, prepared, 12)
                } else emptyList()

                activity.runOnUiThread {
                    if (generation != searchGeneration || !activity.searchMode || searchResults !== target) {
                        return@runOnUiThread
                    }
                    pendingSearch = null
                    settings.publish(query, settingsWork)
                    lastApps = matchingApps
                    lastContacts = matchingContacts
                    lastFiles = matchingFiles
                    displaySearch(target, query, matchingApps, matchingContacts, matchingFiles)
                    if (contactLive) live.queryContacts(generation, query, prepared)
                    if (fileLive) live.queryFiles(generation, query, prepared)
                }
            }
        }
        pendingSearch = task
        searchHandler.postDelayed(task, 80L)
    }

    private fun refreshLiveDisplay(query: String) {
        val target = searchResults ?: return
        displaySearch(target, query, lastApps, lastContacts, lastFiles)
    }

    fun displaySearch(
        target: LinearLayout,
        query: String,
        matchingApps: List<App>,
        matchingContacts: List<ContactIndex.Contact>,
        matchingFiles: List<IndexedFile>,
    ) = with(activity) {
        searchScreen.render(
            target = target,
            query = query,
            calculation = if (query == settings.query && configController.config.search.calculator) {
                settings.calculation
            } else SearchCalculator.Result.NotCalculation,
            openCalculator = { settings.calculatorActions.open() },
            apps = matchingApps.map { app ->
                SearchScreen.AppRow(
                    app.key,
                    app.label,
                    catalogController.iconCache[app.key],
                    open = {
                        runCatching {
                            launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null)
                        }.onFailure {
                            message("This app is unavailable")
                            catalogController.loadApps()
                        }
                    },
                    menu = { actionController.appMenu(app) },
                )
            },
            appState = catalogController.state,
            retryApps = { catalogController.loadApps() },
            settingsUnavailable = configController.config.search.androidSettings && settings.androidUnavailable,
            retrySettings = { refreshSettings() },
            groveSettings = (
                if (query == settings.query && configController.config.search.groveSettings) settings.matches.grove
                else emptyList()
            ).map { SettingsSearchPresentation.row(this, settings.router, it) },
            androidSettings = (
                if (query == settings.query && configController.config.search.androidSettings) settings.matches.android
                else emptyList()
            ).map { SettingsSearchPresentation.row(this, settings.router, it) },
            contacts = (
                if (configController.config.search.contacts && access.hasContacts()) {
                    if (configController.config.search.contactIndexing &&
                        sources.contactCacheReady && !sources.contactLoadFailed
                    ) matchingContacts else live.contacts
                } else emptyList()
            ).map { contact ->
                SearchScreen.ContactRow(contact.id, contact.name) { actionController.contactMenu(contact) }
            },
            files = (
                if (configController.config.search.files && access.hasFiles()) {
                    if (configController.config.search.fileIndexing &&
                        sources.fileCacheReady && !sources.fileLoadFailed
                    ) matchingFiles else live.files
                } else emptyList()
            ).map { file ->
                SearchScreen.FileRow(
                    file,
                    open = { actionController.openFile(file) },
                    menu = { actionController.searchItemMenu(file) },
                )
            },
            contactState = (
                if (configController.config.search.contacts && access.hasContacts()) live.contactState else null
            ) ?: SearchSourceState.resolve(
                configController.config.search.contacts,
                access.hasContacts(),
                indexingContacts && !sources.contactCacheReady,
                contactLoadFailed,
                contacts.size,
                sources.contactScanSkipped,
            ),
            requestContactAccess = access::explainContacts,
            retryContacts = {
                if (configController.config.search.contactIndexing) refreshContacts() else renderSearch(query)
            },
            fileState = (
                if (configController.config.search.files && access.hasFiles()) live.fileState else null
            ) ?: SearchSourceState.resolve(
                configController.config.search.files,
                access.hasFiles(),
                indexingFiles && !sources.fileCacheReady,
                fileLoadFailed,
                files.size,
                fileScanSkipped,
            ),
            requestFileAccess = access::explainFiles,
            retryFiles = {
                if (configController.config.search.fileIndexing) indexFiles() else renderSearch(query)
            },
            searchGoogle = {
                actionController.openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}")
            },
            googleMenu = { actionController.webResultMenu(query.trim(), "Google") },
            searchStore = { actionController.openPlayStore(query.trim()) },
            storeMenu = { actionController.playStoreMenu(query.trim()) },
        )
    }
}

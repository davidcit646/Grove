package tech.granet.grove

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.os.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.*
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.wallpaperLabel
import tech.granet.grove.ui.message
import java.util.*
import java.io.File
import java.util.concurrent.Executors

internal object SearchPublicationGate {
    fun allowed(generation: Int, currentGeneration: Int, active: Boolean,
                enabled: Boolean, access: Boolean, cacheSupersedesLive: Boolean = false): Boolean =
        generation == currentGeneration && active && enabled && access && !cacheSupersedesLive
}

/** Query execution and optional source state. Permissions close the affected source; stale queries cannot publish. */
internal class SearchController(private val activity: MainActivity) {
    internal val searchWorker = Executors.newSingleThreadExecutor()
    private val liveContactWorker = Executors.newSingleThreadExecutor()
    private val liveFileWorker = Executors.newSingleThreadExecutor()
    internal val searchHandler = Handler(Looper.getMainLooper())
    @Volatile internal var searchGeneration = 0
    internal var pendingSearch: Runnable? = null
    internal var searchResults: LinearLayout? = null
    internal var searchField: EditText? = null
    private var liveContacts = emptyList<ContactIndex.Contact>()
    private var liveFiles = emptyList<IndexedFile>()
    private var liveContactState: SearchSourceState? = null
    private var liveFileState: SearchSourceState? = null
    private var liveContactCancellation: android.os.CancellationSignal? = null
    internal val sources by lazy { with(activity) {
        SearchSources(this, worker, contactWorker, { configController.config.search }, this@SearchController::hasContactAccess) {
            if (searchMode) refreshSources()
        }
    } }
    internal val contacts get() = with(activity) { sources.contacts }
    internal val contactSearch get() = with(activity) { sources.contactSearch }
    internal val files get() = with(activity) { sources.files }
    internal val fileSearch get() = with(activity) { sources.fileSearch }
    internal val indexingContacts get() = with(activity) { sources.indexingContacts }
    internal val contactLoadFailed get() = with(activity) { sources.contactLoadFailed }
    internal val lastContactRefresh get() = with(activity) { sources.lastContactRefresh }
    internal val indexingFiles get() = with(activity) { sources.indexingFiles }
    internal val fileLoadFailed get() = with(activity) { sources.fileLoadFailed }
    internal val fileScanSkipped get() = with(activity) { sources.fileScanSkipped }
    private val settingsRouter by lazy { AndroidSettingsRouter(activity) }
    private val groveMatcher = SettingsMatcher(SettingsLabels.localize(activity, SettingsCatalogue.grove))
    private var androidSettings = emptyList<SettingsEntry>()
    private var androidMatcher = SettingsMatcher(emptyList())
    private var androidSettingsFailed = false
    private var settingsRefreshGeneration = 0
    private var settingsMatches = SettingsMatches(emptyList(), emptyList())
    private var settingsQuery = ""
    private var calculation: SearchCalculator.Result = SearchCalculator.Result.NotCalculation
    private val calculatorActions by lazy { CalculatorActions(activity) }

    internal fun refreshSettings() {
        val generation = ++settingsRefreshGeneration
        if (!activity.configController.config.search.androidSettings || !activity.searchMode) {
            androidSettings = emptyList(); androidMatcher = SettingsMatcher(emptyList()); androidSettingsFailed = false
            return
        }
        if (searchWorker.isShutdown) return
        searchWorker.execute {
            if (generation != settingsRefreshGeneration || !activity.configController.config.search.androidSettings) return@execute
            val capabilities = settingsRouter.snapshot()
            val snapshot = SettingsLabels.localize(activity, capabilities.entries)
            activity.runOnUiThread {
                if (generation != settingsRefreshGeneration || activity.isDestroyed || !activity.searchMode || !activity.configController.config.search.androidSettings) return@runOnUiThread
                if (androidSettings != snapshot || androidSettingsFailed != capabilities.failed) {
                    androidSettingsFailed = capabilities.failed
                    androidSettings = snapshot
                    androidMatcher = SettingsMatcher(snapshot)
                    renderSearch(searchField?.text?.toString().orEmpty())
                }
            }
        }
    }
    private var lastApps = emptyList<App>()
    private var lastContacts = emptyList<ContactIndex.Contact>()
    private var lastFiles = emptyList<IndexedFile>()
    internal val searchScreen by lazy { with(activity) { SearchScreen(this) } }

    private var sourceSnapshot: List<Any?> = emptyList()
    private fun sourceKey(): List<Any?> = listOf(sources.contactSearch, sources.fileSearch,
        sources.contactCacheReady, sources.fileCacheReady, sources.contactLoadFailed, sources.fileLoadFailed,
        sources.contactScanSkipped, sources.fileScanSkipped,
        (activity.application as GroveApp).contactChanges.changes.value)

    private fun refreshSources() {
        val query = searchField?.text?.toString().orEmpty()
        if (sourceSnapshot != sourceKey()) renderSearch(query)
        else if (pendingSearch == null) refreshLiveDisplay(query)
    }

    fun cancelPending() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        searchGeneration++
        liveContactCancellation?.cancel(); liveContactCancellation = null
        liveContacts = emptyList(); liveFiles = emptyList()
        liveContactState = null; liveFileState = null
    }

    fun shutdown() { liveContactWorker.shutdownNow(); liveFileWorker.shutdownNow() }

    fun reconcileAccess() {
        refreshSettings()
        with(activity) {
            val query = searchField?.text?.toString().orEmpty()
            cancelPending()
            sources.reconcile()
            if (searchMode) {
                displaySearch(searchResults ?: return, query, lastApps, emptyList(), emptyList())
                renderSearch(query)
            }
        }
    }

    fun indexFiles(): Unit = with(activity) { sources.indexFiles()
    }

    fun refreshContacts(): Unit = with(activity) { sources.refreshContacts(IndexRefreshCause.MANUAL)
    }

    fun hasContactAccess(): Boolean = with(activity) {
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestContactAccess() {
        with(activity) { requestContacts.launch(Manifest.permission.READ_CONTACTS) 
        }
    }

    fun explainContactAccess() {
        with(activity) {
            if (hasContactAccess()) { sources.reconcile(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Contact search access")
                .setMessage("Grove reads contact names from Android for on-device search. If Contact indexing is on, names and lookup IDs are saved in Grove's private on-device cache; phone numbers are read only when you choose an action. Grove does not upload them. Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue to Android") { _, _ -> requestContactAccess() }
                .show()
        }
    }

    fun requestFileAccess() {
        with(activity) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            }.onFailure { message("Open Android settings to allow shared storage search") }
        }
    }

    fun explainFileAccess() {
        with(activity) {
            if (Environment.isExternalStorageManager()) { sources.reconcile(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Shared-storage file search access")
                .setMessage("Android's All files access grants Grove broad access to shared storage, but not app-private data or system partitions. Grove searches names and paths without reading contents or uploading them. If File indexing is on, names and paths are saved in Grove's private on-device cache. Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open Android settings") { _, _ -> requestFileAccess() }
                .show()
        }
    }

    fun applySearchSettings(previous: SearchSettings) {
        with(activity) {
            val query = searchField?.text?.toString().orEmpty()
            cancelPending()
            val settings = configController.config.search
            if (SearchSettingsEffects.protectedSources(previous, settings)) sources.reconcile()
            if (previous.androidSettings != settings.androidSettings) refreshSettings()
            if (settings.contacts && (!previous.contacts || (settings.contactIndexing && !previous.contactIndexing)) &&
                !hasContactAccess()) explainContactAccess()
            if (settings.files && (!previous.files || (settings.fileIndexing && !previous.fileIndexing)) &&
                !Environment.isExternalStorageManager()) explainFileAccess()
            if (searchMode) {
                displaySearch(searchResults ?: return, query, lastApps, emptyList(), emptyList())
                renderSearch(query)
            }
        }
    }

    fun showSearch(animate: Boolean = false, skipTutorial: Boolean = false) {
        with(activity) {
            if (isDestroyed || startupController.coreRecoveryVisible) return
            if (!skipTutorial && searchTutorialController.interceptEntry()) return
            homeController.rememberHomeScroll()
            drawerController.clearAppSelection()
            drawer = false; searchMode = true; homeController.base(readableBackdrop = true)
            root.addView(wallpaperLabel("Search", 30f))
            val field = EditText(this).apply {
                hint = getString(R.string.settings_search_hint)
                filters = arrayOf(InputFilter.LengthFilter(256))
                setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xffc1ccc5.toInt())
                contentDescription = getString(R.string.settings_search_description)
            }
            searchField = field
            root.addView(field)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            searchResults = list
            root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(homeController.button("Home") { homeController.animateDrawerClosed() })
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSearch(s.toString())
                override fun afterTextChanged(s: Editable?) {}
            })
            refreshSettings()
            renderSearch("")
            if (animate) homeController.enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
            field.requestFocus()
            field.post { if (searchMode && searchField === field) getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0) }
        }
    }

    fun renderSearch(query: String) {
        with(activity) {
            val target = searchResults ?: return
            val generation = ++searchGeneration
            liveContactCancellation?.cancel(); liveContactCancellation = null
            liveContacts = emptyList(); liveFiles = emptyList()
            liveContactState = null; liveFileState = null
            pendingSearch?.let(searchHandler::removeCallbacks)
            pendingSearch = null
            val prepared = Search.prepare(query)
            if (prepared.text.isEmpty()) {
                displaySearch(target, query, emptyList(), emptyList(), emptyList())
                return
            }
            val settingsSnapshot = androidSettings
            val matcherSnapshot = androidMatcher
            val appSnapshot = catalogController.appSearch
            val contactSnapshot = contactSearch
            val fileSnapshot = fileSearch
            val config = configController.config.search
            val contactLive = config.contacts && (!config.contactIndexing || !sources.contactCacheReady || sources.contactLoadFailed) && hasContactAccess()
            val fileLive = config.files && (!config.fileIndexing || !sources.fileCacheReady || sources.fileLoadFailed) && Environment.isExternalStorageManager()
            if (contactLive) liveContactState = SearchSourceState.Loading
            if (fileLive) liveFileState = SearchSourceState.Loading
            sourceSnapshot = sourceKey()
            // Keep the committed rows through the debounce; publish a complete frame atomically.
            // Permission/settings reconciliation explicitly removes protected rows before this path.
            val task = Runnable {
                if (searchWorker.isShutdown) return@Runnable
                searchWorker.execute {
                    if (generation != searchGeneration) return@execute
                    val calculated = if (config.calculator) SearchCalculator.calculate(query) else SearchCalculator.Result.NotCalculation
                    val groveMatches = if (config.groveSettings) groveMatcher.matching(query) else emptyList()
                    val androidMatches = if (config.androidSettings) SettingsSearchFallback.rows(matcherSnapshot.matching(query), settingsSnapshot) else emptyList()
                    val matchingApps = SearchResults.matching(appSnapshot, prepared, 12)
                    if (generation != searchGeneration) return@execute
                    val matchingContacts = if (config.contacts && config.contactIndexing && hasContactAccess())
                        SearchResults.matching(contactSnapshot, prepared, 12) else emptyList()
                    if (generation != searchGeneration) return@execute
                    val matchingFiles = if (config.files && config.fileIndexing && Environment.isExternalStorageManager())
                        SearchResults.matching(fileSnapshot, prepared, 12) else emptyList()
                    runOnUiThread {
                        if (generation != searchGeneration || !searchMode || searchResults !== target) return@runOnUiThread
                        pendingSearch = null
                        settingsQuery = query; calculation = calculated; settingsMatches = SettingsMatches(groveMatches, androidMatches)
                        lastApps = matchingApps; lastContacts = matchingContacts; lastFiles = matchingFiles
                        displaySearch(target, query, matchingApps, matchingContacts, matchingFiles)
                        if (contactLive) queryLiveContacts(generation, query, prepared)
                        if (fileLive) queryLiveFiles(generation, query, prepared)
                    }
                }
            }
            pendingSearch = task
            searchHandler.postDelayed(task, 80L)
        }
    }

    private fun queryLiveContacts(generation: Int, query: String, prepared: Search.Query) {
        if (liveContactWorker.isShutdown) return
        val cancellation = android.os.CancellationSignal()
        liveContactCancellation = cancellation
        liveContactWorker.execute {
            val result = runCatching { ContactIndex.load(activity.contentResolver, {
                generation == searchGeneration && activity.configController.config.search.contacts && hasContactAccess()
            }, cancellation, maxDurationMs = 2500L, maxRawRows = 50_000).let { scan -> scan to SearchResults.matching(scan.contacts, prepared, 12) { contact -> contact.searchName } } }
            activity.runOnUiThread {
                if (!SearchPublicationGate.allowed(
                        generation, searchGeneration, !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.contacts, hasContactAccess(),
                        activity.configController.config.search.contactIndexing && sources.contactCacheReady && !sources.contactLoadFailed
                    )) return@runOnUiThread
                result.onSuccess {
                    liveContacts = it.second
                    liveContactState = if (it.first.truncated) SearchSourceState.Partial(liveContacts.size, 1)
                        else SearchSourceState.Ready(liveContacts.size)
                }.onFailure { error ->
                    if (error !is android.os.OperationCanceledException) liveContactState = SearchSourceState.Failed
                }
                refreshLiveDisplay(query)
            }
        }
    }

    private fun queryLiveFiles(generation: Int, query: String, prepared: Search.Query) {
        if (liveFileWorker.isShutdown) return
        liveFileWorker.execute {
            val result = runCatching { FileIndex.scan(Environment.getExternalStorageDirectory(), shouldContinue = {
                generation == searchGeneration && activity.configController.config.search.files &&
                    Environment.isExternalStorageManager()
            }, maxDurationMs = 2500L).let { scan -> scan to SearchResults.matching(scan.files, prepared, 12) { file -> file.searchName } } }
            activity.runOnUiThread {
                if (!SearchPublicationGate.allowed(
                        generation, searchGeneration, !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.files, Environment.isExternalStorageManager(),
                        activity.configController.config.search.fileIndexing && sources.fileCacheReady && !sources.fileLoadFailed
                    )) return@runOnUiThread
                result.onSuccess {
                    liveFiles = it.second
                    liveFileState = SearchSourceState.fromFileScan(it.first, liveFiles.size)
                }.onFailure { liveFileState = SearchSourceState.Failed }
                refreshLiveDisplay(query)
            }
        }
    }

    private fun refreshLiveDisplay(query: String) {
        val target = searchResults ?: return
        displaySearch(target, query, lastApps, lastContacts, lastFiles)
    }

    fun displaySearch(target: LinearLayout, query: String,
                              matchingApps: List<App>, matchingContacts: List<ContactIndex.Contact>,
                              matchingFiles: List<IndexedFile>) {
        with(activity) {
            searchScreen.render(
                target = target, query = query,
                calculation = if (query == settingsQuery && configController.config.search.calculator) calculation else SearchCalculator.Result.NotCalculation,
                openCalculator = { calculatorActions.open() },
                apps = matchingApps.map { app -> SearchScreen.AppRow(app.key, app.label, catalogController.iconCache[app.key],
                    open = {
                        runCatching { launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                            .onFailure { message("This app is unavailable"); catalogController.loadApps() }
                    }, menu = { actionController.appMenu(app) }) },
                appState = catalogController.state, retryApps = { catalogController.loadApps() },
                settingsUnavailable = configController.config.search.androidSettings && androidSettingsFailed, retrySettings = { refreshSettings() },
                groveSettings = (if (query == settingsQuery && configController.config.search.groveSettings) settingsMatches.grove else emptyList()).map { SettingsSearchPresentation.row(this, settingsRouter, it) },
                androidSettings = (if (query == settingsQuery && configController.config.search.androidSettings) settingsMatches.android else emptyList()).map { SettingsSearchPresentation.row(this, settingsRouter, it) },
                contacts = (if (configController.config.search.contacts && hasContactAccess()) {
                    if (configController.config.search.contactIndexing && sources.contactCacheReady && !sources.contactLoadFailed) matchingContacts else liveContacts
                } else emptyList()).map { contact -> SearchScreen.ContactRow(contact.id, contact.name) { actionController.contactMenu(contact) } },
                files = (if (configController.config.search.files && Environment.isExternalStorageManager()) {
                    if (configController.config.search.fileIndexing && sources.fileCacheReady && !sources.fileLoadFailed) matchingFiles else liveFiles
                } else emptyList()).map { file -> SearchScreen.FileRow(file,
                    open = { actionController.openFile(file) }, menu = { actionController.searchItemMenu(file) }) },
                contactState = (if (configController.config.search.contacts && hasContactAccess()) liveContactState else null) ?: SearchSourceState.resolve(configController.config.search.contacts, hasContactAccess(),
                    indexingContacts && !sources.contactCacheReady, contactLoadFailed, contacts.size, sources.contactScanSkipped),
                requestContactAccess = this@SearchController::explainContactAccess, retryContacts = { if (configController.config.search.contactIndexing) refreshContacts() else renderSearch(query) },
                fileState = (if (configController.config.search.files && Environment.isExternalStorageManager()) liveFileState else null) ?: SearchSourceState.resolve(configController.config.search.files, Environment.isExternalStorageManager(),
                    indexingFiles && !sources.fileCacheReady, fileLoadFailed, files.size, fileScanSkipped),
                requestFileAccess = { explainFileAccess() }, retryFiles = { if (configController.config.search.fileIndexing) indexFiles() else renderSearch(query) },
                searchGoogle = { actionController.openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}") },
                googleMenu = { actionController.webResultMenu(query.trim(), "Google") },
                searchStore = { actionController.openPlayStore(query.trim()) },
                storeMenu = { actionController.playStoreMenu(query.trim()) },
            )
        }
    }
}

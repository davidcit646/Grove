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
    internal val sources by lazy { with(activity) {
        SearchSources(this, worker, contactWorker, { configController.config.search }, this@SearchController::hasContactAccess) {
            if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
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
    private var lastApps = emptyList<App>()
    private var lastContacts = emptyList<ContactIndex.Contact>()
    private var lastFiles = emptyList<IndexedFile>()
    internal val searchScreen by lazy { with(activity) { SearchScreen(this) } }

    fun cancelPending() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        searchGeneration++
        liveContacts = emptyList(); liveFiles = emptyList()
        liveContactState = null; liveFileState = null
    }

    fun shutdown() { liveContactWorker.shutdownNow(); liveFileWorker.shutdownNow() }

    fun indexFiles(): Unit = with(activity) { sources.indexFiles()
    }

    fun refreshContacts(): Unit = with(activity) { sources.refreshContacts()
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
                .setMessage("Grove reads contact names from Android for on-device search. If you separately enable Contact indexing, names and lookup IDs are saved in Grove's private on-device cache; phone numbers are read only when you choose an action. Grove does not upload them. Android keeps the permission until you revoke it in system settings.")
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
                .setMessage("Android's All files access grants Grove broad access to shared storage, but not app-private data or system partitions. Grove searches names and paths without reading contents or uploading them. If you separately enable File indexing, names and paths are saved in Grove's private on-device cache. Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open Android settings") { _, _ -> requestFileAccess() }
                .show()
        }
    }

    fun applySearchSettings(previous: SearchSettings) {
        with(activity) {
            sources.reconcile()
            if (configController.config.search.contacts && !previous.contacts && !hasContactAccess()) explainContactAccess()
            if (configController.config.search.files && !previous.files && !Environment.isExternalStorageManager()) explainFileAccess()
            if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        }
    }

    fun showSearch(animate: Boolean = false) {
        with(activity) {
            if (isDestroyed || startupController.coreRecoveryVisible) return
            homeController.rememberHomeScroll()
            drawerController.clearAppSelection()
            drawer = false; searchMode = true; homeController.base()
            root.addView(wallpaperLabel("Search", 30f))
            val field = EditText(this).apply {
                hint = "Apps, contacts, files, web, and Play Store"
                filters = arrayOf(InputFilter.LengthFilter(256))
                setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xffc1ccc5.toInt())
                contentDescription = "Search apps, contacts, files, Google, and Play Store"
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
            liveContacts = emptyList(); liveFiles = emptyList()
            liveContactState = null; liveFileState = null
            pendingSearch?.let(searchHandler::removeCallbacks)
            pendingSearch = null
            val prepared = Search.prepare(query)
            if (prepared.text.isEmpty()) {
                displaySearch(target, query, emptyList(), emptyList(), emptyList())
                return
            }
            val appSnapshot = catalogController.appSearch
            val contactSnapshot = contactSearch
            val fileSnapshot = fileSearch
            val config = configController.config.search
            val contactLive = config.contacts && (!config.contactIndexing || !sources.contactCacheReady) && hasContactAccess()
            val fileLive = config.files && (!config.fileIndexing || !sources.fileCacheReady) && Environment.isExternalStorageManager()
            if (contactLive) liveContactState = SearchSourceState.Loading
            if (fileLive) liveFileState = SearchSourceState.Loading
            target.removeAllViews()
            val task = Runnable {
                if (searchWorker.isShutdown) return@Runnable
                searchWorker.execute {
                    if (generation != searchGeneration) return@execute
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
        liveContactWorker.execute {
            val result = runCatching { ContactIndex.load(activity.contentResolver) {
                generation == searchGeneration && activity.configController.config.search.contacts && hasContactAccess()
            }.let { SearchResults.matching(it, prepared, 12) { contact -> contact.searchName } } }
            activity.runOnUiThread {
                if (generation != searchGeneration || !activity.searchMode || !activity.configController.config.search.contacts ||
                    (activity.configController.config.search.contactIndexing && sources.contactCacheReady) || !hasContactAccess()) return@runOnUiThread
                result.onSuccess {
                    liveContacts = it
                    liveContactState = SearchSourceState.Ready(liveContacts.size)
                }.onFailure { liveContactState = SearchSourceState.Failed }
                refreshLiveDisplay(query)
            }
        }
    }

    private fun queryLiveFiles(generation: Int, query: String, prepared: Search.Query) {
        if (liveFileWorker.isShutdown) return
        liveFileWorker.execute {
            val deadline = android.os.SystemClock.elapsedRealtime() + 2500L
            val result = runCatching { FileIndex.scan(Environment.getExternalStorageDirectory(), shouldContinue = {
                generation == searchGeneration && activity.configController.config.search.files &&
                    Environment.isExternalStorageManager() && android.os.SystemClock.elapsedRealtime() < deadline
            }).let { scan -> scan to SearchResults.matching(scan.files, prepared, 12) { file -> file.searchName } } }
            activity.runOnUiThread {
                if (generation != searchGeneration || !activity.searchMode || !activity.configController.config.search.files ||
                    (activity.configController.config.search.fileIndexing && sources.fileCacheReady) || !Environment.isExternalStorageManager()) return@runOnUiThread
                result.onSuccess {
                    liveFiles = it.second
                    liveFileState = if (it.first.truncated || it.first.skippedDirectories > 0)
                        SearchSourceState.Partial(liveFiles.size, it.first.skippedDirectories + if (it.first.truncated) 1 else 0)
                    else SearchSourceState.Ready(liveFiles.size)
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
                target, query,
                matchingApps.map { app -> SearchScreen.AppRow(app.key, app.label, catalogController.iconCache[app.key],
                    open = {
                        runCatching { launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                            .onFailure { message("This app is unavailable"); catalogController.loadApps() }
                    }, menu = { actionController.appMenu(app) }) },
                (if (configController.config.search.contacts && hasContactAccess()) {
                    if (configController.config.search.contactIndexing && sources.contactCacheReady) matchingContacts else liveContacts
                } else emptyList()).map { contact -> SearchScreen.ContactRow(contact.name) { actionController.contactMenu(contact) } },
                (if (configController.config.search.files && Environment.isExternalStorageManager()) {
                    if (configController.config.search.fileIndexing && sources.fileCacheReady) matchingFiles else liveFiles
                } else emptyList()).map { file -> SearchScreen.FileRow(file,
                    open = { actionController.openFile(file) }, menu = { actionController.searchItemMenu(file) }) },
                (if (configController.config.search.contacts && hasContactAccess()) liveContactState else null) ?: SearchSourceState.resolve(configController.config.search.contacts, hasContactAccess(),
                    indexingContacts, contactLoadFailed, contacts.size),
                this@SearchController::explainContactAccess, { if (configController.config.search.contactIndexing) refreshContacts() else renderSearch(query) },
                (if (configController.config.search.files && Environment.isExternalStorageManager()) liveFileState else null) ?: SearchSourceState.resolve(configController.config.search.files, Environment.isExternalStorageManager(),
                    indexingFiles, fileLoadFailed, files.size, fileScanSkipped),
                requestFileAccess = { explainFileAccess() }, retryFiles = { if (configController.config.search.fileIndexing) indexFiles() else renderSearch(query) },
                searchGoogle = { actionController.openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}") },
                googleMenu = { actionController.webResultMenu(query.trim(), "Google") },
                searchStore = { actionController.openPlayStore(query.trim()) },
                storeMenu = { actionController.playStoreMenu(query.trim()) },
            )
        }
    }
}

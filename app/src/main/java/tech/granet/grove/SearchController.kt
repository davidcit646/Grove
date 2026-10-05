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

/** SearchController owns its lane; Android lifecycle and results remain in MainActivity. */
internal class SearchController(private val activity: MainActivity) {
    internal val searchWorker = Executors.newSingleThreadExecutor()
    internal val searchHandler = Handler(Looper.getMainLooper())
    @Volatile internal var searchGeneration = 0
    internal var pendingSearch: Runnable? = null
    internal var searchResults: LinearLayout? = null
    internal var searchField: EditText? = null
    internal val sources by lazy { with(activity) {
        SearchSources(this, worker, contactWorker, { configController.config.search }, searchController::hasContactAccess) {
            if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
        }
    } }
    internal val contacts get() = with(activity) { searchController.sources.contacts }
    internal val contactSearch get() = with(activity) { searchController.sources.contactSearch }
    internal val files get() = with(activity) { searchController.sources.files }
    internal val fileSearch get() = with(activity) { searchController.sources.fileSearch }
    internal val indexingContacts get() = with(activity) { searchController.sources.indexingContacts }
    internal val contactLoadFailed get() = with(activity) { searchController.sources.contactLoadFailed }
    internal val lastContactRefresh get() = with(activity) { searchController.sources.lastContactRefresh }
    internal val indexingFiles get() = with(activity) { searchController.sources.indexingFiles }
    internal val fileLoadFailed get() = with(activity) { searchController.sources.fileLoadFailed }
    internal val fileScanSkipped get() = with(activity) { searchController.sources.fileScanSkipped }
    internal val searchScreen by lazy { with(activity) { SearchScreen(this) } }

    fun indexFiles(): Unit = with(activity) { searchController.sources.indexFiles()
    }

    fun refreshContacts(): Unit = with(activity) { searchController.sources.refreshContacts()
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
            if (searchController.hasContactAccess()) { searchController.refreshContacts(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Contact search access")
                .setMessage("Grove reads contact names and phone numbers from Android's Contacts Provider to show search results and contact actions. Results stay in memory on this device; Grove does not upload or save a contact copy. If you choose Call or Text, Android passes that number to the app you select. You can skip this and turn Contact search off at any time. The Android permission remains granted until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue to Android") { _, _ -> searchController.requestContactAccess() }
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
            if (Environment.isExternalStorageManager()) { searchController.indexFiles(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Shared-storage file search access")
                .setMessage("Android's All files access grants Grove broad read and write access to shared storage, including files beyond photos and videos. It does not grant access to other apps' private data or system partitions. Grove uses it to read file names and paths for on-device search; it does not read file contents, modify files, or upload the index. Opening a result shares that one file with the app you select. This is optional. Turning File search off clears Grove's in-memory index, but Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open Android settings") { _, _ -> searchController.requestFileAccess() }
                .show()
        }
    }

    fun applySearchSettings(previous: SearchSettings) {
        with(activity) {
            if (!configController.config.search.contacts) searchController.sources.clearContacts()
            else if (!previous.contacts) {
                if (searchController.hasContactAccess()) searchController.refreshContacts() else searchController.explainContactAccess()
            }
            if (!configController.config.search.files) searchController.sources.clearFiles()
            else if (!previous.files) {
                if (Environment.isExternalStorageManager()) searchController.indexFiles() else searchController.explainFileAccess()
            }
            if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
        }
    }

    fun showSearch(animate: Boolean = false) {
        with(activity) {
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
            searchController.searchField = field
            root.addView(field)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            searchController.searchResults = list
            root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(homeController.button("Home") { homeController.animateDrawerClosed() })
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = searchController.renderSearch(s.toString())
                override fun afterTextChanged(s: Editable?) {}
            })
            searchController.renderSearch("")
            if (animate) homeController.enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
            field.requestFocus()
            field.post { if (searchMode && searchController.searchField === field) getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0) }
        }
    }

    fun renderSearch(query: String) {
        with(activity) {
            val target = searchController.searchResults ?: return
            val generation = ++searchController.searchGeneration
            searchController.pendingSearch?.let(searchController.searchHandler::removeCallbacks)
            searchController.pendingSearch = null
            val prepared = Search.prepare(query)
            if (prepared.text.isEmpty()) {
                searchController.displaySearch(target, query, emptyList(), emptyList(), emptyList())
                return
            }
            val appSnapshot = catalogController.appSearch
            val contactSnapshot = searchController.contactSearch
            val fileSnapshot = searchController.fileSearch
            target.removeAllViews()
            val task = Runnable {
                if (searchController.searchWorker.isShutdown) return@Runnable
                searchController.searchWorker.execute {
                    if (generation != searchController.searchGeneration) return@execute
                    val matchingApps = SearchResults.matching(appSnapshot, prepared, 12)
                    if (generation != searchController.searchGeneration) return@execute
                    val matchingContacts = SearchResults.matching(contactSnapshot, prepared, 12)
                    if (generation != searchController.searchGeneration) return@execute
                    val matchingFiles = SearchResults.matching(fileSnapshot, prepared, 12)
                    runOnUiThread {
                        if (generation != searchController.searchGeneration || !searchMode || searchController.searchResults !== target) return@runOnUiThread
                        searchController.pendingSearch = null
                        searchController.displaySearch(target, query, matchingApps, matchingContacts, matchingFiles)
                    }
                }
            }
            searchController.pendingSearch = task
            searchController.searchHandler.postDelayed(task, 80L)
        }
    }

    fun displaySearch(target: LinearLayout, query: String,
                              matchingApps: List<App>, matchingContacts: List<ContactIndex.Contact>,
                              matchingFiles: List<IndexedFile>) {
        with(activity) {
            searchController.searchScreen.render(
                target, query,
                matchingApps.map { app -> SearchScreen.AppRow(app.key, app.label, catalogController.iconCache[app.key],
                    open = {
                        runCatching { launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                            .onFailure { message("This app is unavailable"); catalogController.loadApps() }
                    }, menu = { actionController.appMenu(app) }) },
                matchingContacts.map { contact -> SearchScreen.ContactRow(contact.name) { actionController.contactMenu(contact) } },
                matchingFiles.map { file -> SearchScreen.FileRow(file,
                    open = { actionController.openFile(file) }, menu = { actionController.searchItemMenu(file) }) },
                SearchSourceState.resolve(configController.config.search.contacts, searchController.hasContactAccess(),
                    searchController.indexingContacts, searchController.contactLoadFailed, searchController.contacts.size),
                searchController::explainContactAccess, searchController::refreshContacts,
                SearchSourceState.resolve(configController.config.search.files, Environment.isExternalStorageManager(),
                    searchController.indexingFiles, searchController.fileLoadFailed, searchController.files.size, searchController.fileScanSkipped),
                requestFileAccess = { searchController.explainFileAccess() }, retryFiles = { searchController.indexFiles() },
                searchGoogle = { actionController.openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}") },
                googleMenu = { actionController.webResultMenu(query.trim(), "Google") },
                searchStore = { actionController.openPlayStore(query.trim()) },
                storeMenu = { actionController.playStoreMenu(query.trim()) },
            )
        }
    }
}

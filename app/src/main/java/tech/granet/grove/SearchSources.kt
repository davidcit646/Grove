package tech.granet.grove

import android.database.ContentObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.ExecutorService

/** UI snapshots of independent GFI/GCI caches. Android and the user's switches authorize each read. */
internal class SearchSources(
    private val activity: AppCompatActivity,
    private val worker: ExecutorService,
    private val contactWorker: ExecutorService,
    private val settings: () -> SearchSettings,
    private val hasContactAccess: () -> Boolean,
    private val redraw: () -> Unit,
) {
    var contacts = emptyList<ContactIndex.Contact>(); private set
    var contactSearch = SearchResults.prepare(contacts) { it.searchName }; private set
    var files = emptyList<IndexedFile>(); private set
    var fileSearch = SearchResults.prepare(files) { it.searchName }; private set
    var indexingContacts = false; private set
    var contactLoadFailed = false; private set
    var lastContactRefresh = 0L; private set
    var indexingFiles = false; private set
    var fileLoadFailed = false; private set
    var fileScanSkipped = 0; private set
    var contactScanSkipped = 0; private set
    var contactCacheReady = false; private set
    var fileCacheReady = false; private set
    private var contactCorrupt = false
    private var fileCorrupt = false
    @Volatile private var contactGeneration = 0
    @Volatile private var fileGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    private val contactChange = Runnable { refreshContacts(IndexRefreshCause.PROVIDER_CHANGE) }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(contactChange)
            handler.postDelayed(contactChange, 400L)
        }
    }
    private var observing = false

    init {
        try {
            val work = WorkManager.getInstance(activity)
            work.getWorkInfosForUniqueWorkLiveData(IndexWork.name("contacts")).observe(activity, Observer { infos ->
                val info = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(activity, "contacts") }
                indexingContacts = info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.RUNNING
                contactLoadFailed = info?.state == WorkInfo.State.FAILED
                if (info?.state == WorkInfo.State.SUCCEEDED) loadContacts() else redraw()
            })
            work.getWorkInfosForUniqueWorkLiveData(IndexWork.name("files")).observe(activity, Observer { infos ->
                val info = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(activity, "files") }
                indexingFiles = info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.RUNNING
                fileLoadFailed = info?.state == WorkInfo.State.FAILED
                if (info?.state == WorkInfo.State.SUCCEEDED) loadFiles() else redraw()
            })
        } catch (error: Exception) {
            contactLoadFailed = true; fileLoadFailed = true
            Log.w("Grove", "Index scheduling unavailable: ${error.javaClass.simpleName}")
        }
    }

    fun reconcile() {
        if (IndexAccessPolicy.contacts(settings(), hasContactAccess())) {
            if (!observing) runCatching {
                activity.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                observing = true
            }.onFailure { Log.w("Grove", "Contact observer unavailable", it) }
            loadContacts()
            if (!IndexWork.reconcile(activity, "contacts")) contactLoadFailed = true
        } else clearContacts()
        if (IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) {
            loadFiles()
            if (!IndexWork.reconcile(activity, "files")) fileLoadFailed = true
        } else clearFiles()
    }

    fun indexFiles() { if (IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager()) && !IndexWork.enqueue(activity, "files", IndexRefreshCause.MANUAL)) { fileLoadFailed = true; redraw() } }
    fun refreshContacts(cause: IndexRefreshCause = IndexRefreshCause.MANUAL) { if (IndexAccessPolicy.contacts(settings(), hasContactAccess()) && !IndexWork.enqueue(activity, "contacts", cause)) { contactLoadFailed = true; redraw() } }

    private fun loadContacts() {
        if (!IndexAccessPolicy.contacts(settings(), hasContactAccess())) return
        val generation = ++contactGeneration
        contactWorker.execute {
            val cache = try { Result.success(run {
                val snapshot = IndexCache.contacts(activity)
                snapshot to snapshot?.let { SearchResults.prepare(it.items) { contact -> contact.searchName } }
            }) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                if (generation != contactGeneration || activity.isDestroyed || !IndexAccessPolicy.contacts(settings(), hasContactAccess())) return@runOnUiThread
                cache.onSuccess { (snapshot, prepared) ->
                    if (snapshot != null && prepared != null) {
                        contactCorrupt = false
                        contactCacheReady = System.currentTimeMillis() - snapshot.writtenAt in 0..(15L * 60_000)
                        contactScanSkipped = snapshot.skipped
                        if (contacts != snapshot.items) {
                            contacts = snapshot.items
                            contactSearch = prepared
                        }
                        lastContactRefresh = android.os.SystemClock.elapsedRealtime()
                    }
                }.onFailure {
                    contactLoadFailed = true
                    contactCorrupt = true
                    IndexCache.clear(activity, "contacts")
                    refreshContacts(IndexRefreshCause.REPAIR)
                }
                redraw()
            }
        }
    }

    private fun loadFiles() {
        if (!IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) return
        val generation = ++fileGeneration
        worker.execute {
            val cache = try { Result.success(run {
                val snapshot = IndexCache.files(activity)
                snapshot to snapshot?.let { SearchResults.prepare(it.items) { file -> file.searchName } }
            }) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                if (generation != fileGeneration || activity.isDestroyed || !IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) return@runOnUiThread
                cache.onSuccess { (snapshot, prepared) ->
                    if (snapshot != null && prepared != null) {
                        fileCorrupt = false
                        fileCacheReady = System.currentTimeMillis() - snapshot.writtenAt in 0..(24L * 60 * 60_000)
                        if (files != snapshot.items) {
                            files = snapshot.items
                            fileSearch = prepared
                        }
                        fileScanSkipped = snapshot.skipped
                    }
                }.onFailure {
                    fileLoadFailed = true
                    fileCorrupt = true
                    IndexCache.clear(activity, "files")
                    indexFiles()
                }
                redraw()
            }
        }
    }

    fun clearContacts(notify: Boolean = true) {
        contactGeneration++
        contacts = emptyList(); contactSearch = SearchResults.prepare(contacts) { it.searchName }
        contactCacheReady = false
        contactScanSkipped = 0
        contactCorrupt = false
        indexingContacts = false; contactLoadFailed = false; lastContactRefresh = 0
        handler.removeCallbacks(contactChange)
        if (observing) runCatching { activity.contentResolver.unregisterContentObserver(observer) }
        observing = false
        IndexWork.cancel(activity, "contacts")
        if (notify) redraw()
    }

    fun clearFiles(notify: Boolean = true) {
        fileGeneration++
        files = emptyList(); fileSearch = SearchResults.prepare(files) { it.searchName }
        fileCacheReady = false
        fileCorrupt = false
        indexingFiles = false; fileLoadFailed = false; fileScanSkipped = 0
        IndexWork.cancel(activity, "files")
        if (notify) redraw()
    }

    fun shutdown() {
        contactGeneration++; fileGeneration++
        handler.removeCallbacks(contactChange)
        if (observing) runCatching { activity.contentResolver.unregisterContentObserver(observer) }
        observing = false
        contactWorker.shutdownNow()
        // Persistent work survives Activity destruction.
    }

    fun status(kind: String): String {
        val files = kind == "files"
        val enabled = if (files) settings().fileIndexing else settings().contactIndexing
        val permitted = if (files) Environment.isExternalStorageManager() else hasContactAccess()
        val exists = java.io.File(activity.filesDir, "grove-$kind-index.json").exists()
        val state = IndexState.resolve(enabled, permitted, exists,
            if (files) fileCacheReady else contactCacheReady,
            if (files) indexingFiles else indexingContacts,
            if (files) fileLoadFailed else contactLoadFailed,
            if (files) fileCorrupt else contactCorrupt,
            partial = if (files) fileScanSkipped > 0 else contactScanSkipped > 0)
        return if (!(if (files) settings().files else settings().contacts)) "Search disabled"
        else if (enabled && !permitted) "Permission required" else state.label
    }
}

package tech.granet.grove

import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager

/** UI snapshots of independent GFI/GCI caches. Android and the user's switches authorize each read. */
internal class SearchSources(
    private val activity: AppCompatActivity,
    private val settings: () -> SearchSettings,
    private val hasContactAccess: () -> Boolean,
    private val redraw: () -> Unit,
) {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val contactWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
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
    val contactCacheReady get() = IndexCache.metadata("contacts").fresh("contacts") && contactWrittenAt == IndexCache.metadata("contacts").writtenAt
    private var contactWrittenAt = 0L
    val fileCacheReady get() = IndexCache.metadata("files").fresh("files") && fileWrittenAt == IndexCache.metadata("files").writtenAt
    private var fileWrittenAt = 0L
    private var contactCorrupt = false
    private var fileCorrupt = false
    @Volatile private var contactGeneration = 0
    @Volatile private var fileGeneration = 0
    private var loadedContacts = -1L
    private var loadedFiles = -1L

    init {
        (activity.application as GroveApp).contactChanges.changes.observe(activity) { redraw() }
        IndexCache.changes.observe(activity) { loadContacts(); loadFiles() }
        IndexWork.failures.observe(activity) { errors ->
            if (errors.containsKey("contacts")) contactLoadFailed = true
            if (errors.containsKey("files")) fileLoadFailed = true
            redraw()
        }
        try {
            val work = WorkManager.getInstance(activity)
            work.getWorkInfosForUniqueWorkLiveData(IndexWork.name("contacts")).observe(activity, Observer { infos ->
                val info = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(activity, "contacts") }
                indexingContacts = info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.RUNNING
                contactLoadFailed = info?.state == WorkInfo.State.FAILED
                redraw()
            })
            work.getWorkInfosForUniqueWorkLiveData(IndexWork.name("files")).observe(activity, Observer { infos ->
                val info = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(activity, "files") }
                indexingFiles = info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.RUNNING
                fileLoadFailed = info?.state == WorkInfo.State.FAILED
                redraw()
            })
        } catch (error: Exception) {
            contactLoadFailed = true; fileLoadFailed = true
            Log.w("Grove", "Index scheduling unavailable: ${error.javaClass.simpleName}")
        }
    }

    fun reconcile() {
        (activity.application as GroveApp).contactChanges.reconcile()
        if (IndexAccessPolicy.contacts(settings(), hasContactAccess())) {
            loadContacts()
            if (!IndexWork.reconcile(activity, "contacts")) contactLoadFailed = true
        } else clearContacts()
        if (IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) {
            loadFiles()
            if (!IndexWork.reconcile(activity, "files")) fileLoadFailed = true
        } else clearFiles()
    }

    fun indexFiles() { if (IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager()) && !IndexWork.enqueue(activity, "files", IndexRefreshCause.MANUAL)) { fileLoadFailed = true; redraw() } }
    fun refreshContacts(cause: IndexRefreshCause) { if (IndexAccessPolicy.contacts(settings(), hasContactAccess()) && !IndexWork.enqueue(activity, "contacts", cause)) { contactLoadFailed = true; redraw() } }

    private fun loadContacts() {
        if (!IndexAccessPolicy.contacts(settings(), hasContactAccess())) return
        val revision = IndexCache.generation("contacts")
        if (loadedContacts == revision) return
        loadedContacts = revision
        val generation = ++contactGeneration
        contactWorker.execute {
            val cache = try { Result.success(run {
                val snapshot = IndexCache.contacts(activity)
                snapshot to snapshot?.let { SearchResults.prepare(it.items) { contact -> contact.searchName } }
            }) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                if (generation != contactGeneration || revision != IndexCache.generation("contacts") || activity.isDestroyed || !IndexAccessPolicy.contacts(settings(), hasContactAccess())) return@runOnUiThread
                cache.onSuccess { (snapshot, prepared) ->
                    if (snapshot != null && prepared != null) {
                        contactCorrupt = false
                        contactWrittenAt = snapshot.writtenAt
                        contactLoadFailed = false
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
                    refreshContacts(IndexRefreshCause.REPAIR)
                }
                redraw()
            }
        }
    }

    private fun loadFiles() {
        if (!IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) return
        val revision = IndexCache.generation("files")
        if (loadedFiles == revision) return
        loadedFiles = revision
        val generation = ++fileGeneration
        worker.execute {
            val cache = try { Result.success(run {
                val snapshot = IndexCache.files(activity)
                snapshot to snapshot?.let { SearchResults.prepare(it.items) { file -> file.searchName } }
            }) } catch (error: Exception) { Result.failure(error) }
            activity.runOnUiThread {
                if (generation != fileGeneration || revision != IndexCache.generation("files") || activity.isDestroyed || !IndexAccessPolicy.files(settings(), Environment.isExternalStorageManager())) return@runOnUiThread
                cache.onSuccess { (snapshot, prepared) ->
                    if (snapshot != null && prepared != null) {
                        fileCorrupt = false
                        fileWrittenAt = snapshot.writtenAt
                        fileLoadFailed = false
                        if (files != snapshot.items) {
                            files = snapshot.items
                            fileSearch = prepared
                        }
                        fileScanSkipped = snapshot.skipped
                    }
                }.onFailure {
                    fileLoadFailed = true
                    fileCorrupt = true
                    IndexWork.enqueue(activity, "files", IndexRefreshCause.REPAIR)
                }
                redraw()
            }
        }
    }

    fun clearContacts(notify: Boolean = true) {
        contactGeneration++
        loadedContacts = -1L
        contacts = emptyList(); contactSearch = SearchResults.prepare(contacts) { it.searchName }
        contactWrittenAt = 0L
        contactScanSkipped = 0
        contactCorrupt = false
        indexingContacts = false; contactLoadFailed = false; lastContactRefresh = 0
        IndexWork.cancel(activity, "contacts")
        if (notify) redraw()
    }

    fun clearFiles(notify: Boolean = true) {
        fileGeneration++
        loadedFiles = -1L
        files = emptyList(); fileSearch = SearchResults.prepare(files) { it.searchName }
        fileWrittenAt = 0L
        fileCorrupt = false
        indexingFiles = false; fileLoadFailed = false; fileScanSkipped = 0
        IndexWork.cancel(activity, "files")
        if (notify) redraw()
    }

    fun shutdown() {
        contactGeneration++; fileGeneration++
        contactWorker.shutdownNow()
        worker.shutdownNow()
        // Persistent work survives Activity destruction.
    }

    fun status(kind: String): String {
        val files = kind == "files"
        val enabled = if (files) settings().fileIndexing else settings().contactIndexing
        val permitted = if (files) Environment.isExternalStorageManager() else hasContactAccess()
        val exists = IndexCache.metadata(kind).validity == IndexValidity.AVAILABLE
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

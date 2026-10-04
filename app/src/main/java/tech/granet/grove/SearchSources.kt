package tech.granet.grove

import android.database.ContentObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ExecutorService
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.message

/** Optional search sources own their indexes, cancellation, and provider failures. */
internal class SearchSources(
    private val activity: AppCompatActivity,
    private val worker: ExecutorService,
    private val contactWorker: ExecutorService,
    private val settings: () -> SearchSettings,
    private val hasContactAccess: () -> Boolean,
    private val redraw: () -> Unit,
) {
    var contacts = emptyList<ContactIndex.Contact>()
        private set
    var contactSearch = SearchResults.prepare(contacts) { it.searchName }
        private set
    var files = emptyList<IndexedFile>()
        private set
    var fileSearch = SearchResults.prepare(files) { it.searchName }
        private set
    var indexingContacts = false
        private set
    var contactLoadFailed = false
        private set
    var lastContactRefresh = 0L
        private set
    var indexingFiles = false
        private set
    var fileLoadFailed = false
        private set
    var fileScanSkipped = 0
        private set
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var contactGeneration = 0
    @Volatile private var fileIndexGeneration = 0
    private var contactObserverRegistered = false
    private var contactWarningShown = false
    private val delayedContactRefresh = Runnable { refreshContacts() }
    private val contactObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(delayedContactRefresh)
            handler.postDelayed(delayedContactRefresh, 400L)
        }
    }

    fun indexFiles() {
        if (!settings().files || !Environment.isExternalStorageManager() || indexingFiles) return
        val generation = ++fileIndexGeneration
        indexingFiles = true
        fileLoadFailed = false
        redraw()
        worker.execute {
            if (generation != fileIndexGeneration) return@execute
            val result = runCatching {
                val scan = FileIndex.scan(Environment.getExternalStorageDirectory(),
                    shouldContinue = { generation == fileIndexGeneration })
                scan to SearchResults.prepare(scan.files) { it.searchName }
            }
            activity.runOnUiThread(Runnable {
                if (generation != fileIndexGeneration || activity.isDestroyed) return@Runnable
                indexingFiles = false
                result.onSuccess {
                    files = it.first.files
                    fileSearch = it.second
                    fileScanSkipped = it.first.skippedDirectories
                    fileLoadFailed = false
                }.onFailure {
                    files = emptyList()
                    fileSearch = SearchResults.prepare(files) { it.searchName }
                    fileScanSkipped = 0
                    fileLoadFailed = true
                    Log.w("Grove", "Could not index shared storage", it)
                    activity.message("Could not index shared storage")
                }
                redraw()
            })
        }
    }

    fun refreshContacts() {
        if (!settings().contacts || !hasContactAccess()) {
            clearContacts()
            return
        }
        if (!contactObserverRegistered) {
            runCatching { activity.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                true, contactObserver); contactObserverRegistered = true }
                .onFailure { Log.w("Grove", "Contact observer unavailable", it) }
        }
        if (contactWorker.isShutdown) return
        indexingContacts = true
        contactLoadFailed = false
        redraw()
        val generation = ++contactGeneration
        contactWorker.execute {
            if (generation != contactGeneration) return@execute
            val result = runCatching {
                val loaded = ContactIndex.load(activity.contentResolver) { generation == contactGeneration }
                loaded to SearchResults.prepare(loaded) { it.searchName }
            }
            activity.runOnUiThread {
                if (activity.isDestroyed || generation != contactGeneration || !settings().contacts || !hasContactAccess()) return@runOnUiThread
                indexingContacts = false
                result.onSuccess {
                    contacts = it.first
                    contactSearch = it.second
                    lastContactRefresh = SystemClock.elapsedRealtime()
                    contactLoadFailed = false
                    redraw()
                    if (it.first.isEmpty() && !contactWarningShown) {
                        contactWarningShown = true
                        activity.infoDialog("No device contacts found",
                            "Grove can search contacts available through Android. If your contacts are kept only inside another app, enable its device contact sync.")
                    }
                }
                    .onFailure {
                        contacts = emptyList()
                        contactSearch = SearchResults.prepare(contacts) { it.searchName }
                        contactLoadFailed = true
                        redraw()
                        Log.w("Grove", "Contacts provider unavailable", it)
                        if (!contactWarningShown) {
                            contactWarningShown = true
                            activity.infoDialog("Contact search unavailable",
                                "Grove couldn't read the device's contacts provider. Check that a contacts app is enabled and contact access is allowed.")
                        }
                    }
            }
        }
    }


    fun clearContacts(notify: Boolean = true) {
        contactGeneration++
        contacts = emptyList()
        contactSearch = SearchResults.prepare(contacts) { it.searchName }
        indexingContacts = false
        contactLoadFailed = false
        handler.removeCallbacks(delayedContactRefresh)
        if (contactObserverRegistered) {
            runCatching { activity.contentResolver.unregisterContentObserver(contactObserver) }
                .onFailure { Log.w("Grove", "Could not unregister contact observer", it) }
            contactObserverRegistered = false
        }
        if (notify) redraw()
    }

    fun clearFiles(notify: Boolean = true) {
        fileIndexGeneration++
        files = emptyList()
        fileSearch = SearchResults.prepare(files) { it.searchName }
        indexingFiles = false
        fileLoadFailed = false
        fileScanSkipped = 0
        if (notify) redraw()
    }

    fun shutdown() {
        clearContacts(notify = false)
        clearFiles(notify = false)
        contactWorker.shutdownNow()
    }
}

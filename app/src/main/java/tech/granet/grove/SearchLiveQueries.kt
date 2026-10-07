package tech.granet.grove

import android.os.CancellationSignal
import android.os.Environment
import java.util.concurrent.Executors

/** Owns bounded live provider/file queries used when a durable index is unavailable or disabled. */
internal class SearchLiveQueries(
    private val activity: MainActivity,
    private val sources: () -> SearchSources,
    private val currentGeneration: () -> Int,
    private val hasContacts: () -> Boolean,
    private val hasFiles: () -> Boolean,
    private val onChanged: (String) -> Unit,
) {
    private val contactWorker = Executors.newSingleThreadExecutor()
    private val fileWorker = Executors.newSingleThreadExecutor()
    private var contactCancellation: CancellationSignal? = null

    var contacts = emptyList<ContactIndex.Contact>()
        private set
    var files = emptyList<IndexedFile>()
        private set
    var contactState: SearchSourceState? = null
        private set
    var fileState: SearchSourceState? = null
        private set

    fun begin(contact: Boolean, file: Boolean) {
        contacts = emptyList()
        files = emptyList()
        contactState = if (contact) SearchSourceState.Loading else null
        fileState = if (file) SearchSourceState.Loading else null
    }

    fun cancel() {
        contactCancellation?.cancel()
        contactCancellation = null
        contacts = emptyList()
        files = emptyList()
        contactState = null
        fileState = null
    }

    fun shutdown() {
        contactWorker.shutdownNow()
        fileWorker.shutdownNow()
    }

    fun queryContacts(generation: Int, query: String, prepared: Search.Query) {
        if (contactWorker.isShutdown) return
        val cancellation = CancellationSignal()
        contactCancellation = cancellation
        contactWorker.execute {
            val result = runCatching {
                ContactIndex.load(
                    activity.contentResolver,
                    {
                        generation == currentGeneration() &&
                            activity.configController.config.search.contacts &&
                            hasContacts()
                    },
                    cancellation,
                    maxDurationMs = 2500L,
                    maxRawRows = 50_000,
                ).let { scan ->
                    scan to SearchResults.matching(scan.contacts, prepared, 12) { contact -> contact.searchName }
                }
            }
            activity.runOnUiThread {
                val source = sources()
                if (!SearchPublicationGate.allowed(
                        generation,
                        currentGeneration(),
                        !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.contacts,
                        hasContacts(),
                        activity.configController.config.search.contactIndexing &&
                            source.contactCacheReady && !source.contactLoadFailed,
                    )
                ) return@runOnUiThread
                result.onSuccess {
                    contacts = it.second
                    contactState = if (it.first.truncated) SearchSourceState.Partial(contacts.size, 1)
                    else SearchSourceState.Ready(contacts.size)
                }.onFailure { error ->
                    if (error !is android.os.OperationCanceledException) contactState = SearchSourceState.Failed
                }
                onChanged(query)
            }
        }
    }

    fun queryFiles(generation: Int, query: String, prepared: Search.Query) {
        if (fileWorker.isShutdown) return
        fileWorker.execute {
            val result = runCatching {
                FileIndex.scan(
                    Environment.getExternalStorageDirectory(),
                    shouldContinue = {
                        generation == currentGeneration() &&
                            activity.configController.config.search.files &&
                            hasFiles()
                    },
                    maxDurationMs = 2500L,
                ).let { scan ->
                    scan to SearchResults.matching(scan.files, prepared, 12) { file -> file.searchName }
                }
            }
            activity.runOnUiThread {
                val source = sources()
                if (!SearchPublicationGate.allowed(
                        generation,
                        currentGeneration(),
                        !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.files,
                        hasFiles(),
                        activity.configController.config.search.fileIndexing &&
                            source.fileCacheReady && !source.fileLoadFailed,
                    )
                ) return@runOnUiThread
                result.onSuccess {
                    files = it.second
                    fileState = SearchSourceState.fromFileScan(it.first, files.size)
                }.onFailure {
                    fileState = SearchSourceState.Failed
                }
                onChanged(query)
            }
        }
    }
}

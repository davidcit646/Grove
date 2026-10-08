package tech.granet.grove

import android.os.Environment
import java.util.concurrent.Executors

/** Live-provider execution. Android sources remain authoritative; publication rechecks access. */
internal class SearchLiveQueries(
    private val activity: MainActivity,
    private val currentGeneration: () -> Int,
    private val setCancellation: (android.os.CancellationSignal) -> Unit,
    private val contactResult: (Result<Pair<ContactIndex.ScanResult, List<ContactIndex.Contact>>>) -> Unit,
    private val fileResult: (Result<Pair<FileIndex.ScanResult, List<IndexedFile>>>) -> Unit,
    private val refresh: (String) -> Unit,
) {
    private val contactWorker = Executors.newSingleThreadExecutor()
    private val fileWorker = Executors.newSingleThreadExecutor()
    fun shutdown() { contactWorker.shutdownNow(); fileWorker.shutdownNow() }
    fun queryLiveContacts(generation: Int, query: String, prepared: Search.Query) {
        if (contactWorker.isShutdown) return
        val cancellation = android.os.CancellationSignal()
        setCancellation(cancellation)
        contactWorker.execute {
            val result = runCatching { ContactIndex.load(activity.contentResolver, {
                generation == currentGeneration() && activity.configController.config.search.contacts && activity.searchController.hasContactAccess()
            }, cancellation, maxDurationMs = 2500L, maxRawRows = 50_000).let { scan -> scan to SearchResults.matching(scan.contacts, prepared, 12) { contact -> contact.searchName } } }
            activity.runOnUiThread {
                if (!SearchPublicationGate.allowed(
                        generation, currentGeneration(), !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.contacts, activity.searchController.hasContactAccess(),
                        activity.configController.config.search.contactIndexing && activity.searchController.sources.contactCacheReady && !activity.searchController.sources.contactLoadFailed
                    )) return@runOnUiThread
                contactResult(result)
                refresh(query)
            }
        }
    }

    fun queryLiveFiles(generation: Int, query: String, prepared: Search.Query) {
        if (fileWorker.isShutdown) return
        fileWorker.execute {
            val result = runCatching { FileIndex.scan(Environment.getExternalStorageDirectory(), shouldContinue = {
                generation == currentGeneration() && activity.configController.config.search.files &&
                    Environment.isExternalStorageManager()
            }, maxDurationMs = 2500L).let { scan -> scan to SearchResults.matching(scan.files, prepared, 12) { file -> file.searchName } } }
            activity.runOnUiThread {
                if (!SearchPublicationGate.allowed(
                        generation, currentGeneration(), !activity.isDestroyed && activity.searchMode,
                        activity.configController.config.search.files, Environment.isExternalStorageManager(),
                        activity.configController.config.search.fileIndexing && activity.searchController.sources.fileCacheReady && !activity.searchController.sources.fileLoadFailed
                    )) return@runOnUiThread
                fileResult(result)
                refresh(query)
            }
        }
    }

}

package tech.granet.grove

import android.content.Context

internal class IndexStatusController(private val context: Context, private val repository: SettingsRepository) {
    private val workStates = mutableMapOf<String, androidx.work.WorkInfo?>()
    fun observeIndex(kind: String, infos: List<androidx.work.WorkInfo>) {
        workStates[kind] = infos.firstOrNull { it.id.toString() == IndexWork.currentWorkId(context, kind) }
    }
    fun indexStatus(kind: String, permitted: Boolean): String {
        val setting = repository.snapshot().config.search
        val enabled = if (kind == "files") setting.files else setting.contacts
        val indexed = if (kind == "files") setting.fileIndexing else setting.contactIndexing
        val cache = IndexCache.metadata(kind)
        return when {
            !enabled -> "Search disabled"
            !permitted -> "Android access required"
            !indexed -> "Live search · indexing off"
            cache.validity == IndexValidity.UNKNOWN -> "Checking saved index"
            cache.validity == IndexValidity.CORRUPT -> "Saved index invalid · live search available"
            cache.validity == IndexValidity.ABSENT -> "No saved index · live search available"
            !cache.fresh(kind) -> "Saved index needs a refresh"
            cache.partial -> "Partial saved index · live search available"
            else -> "Saved index available"
        }
    }
    fun indexActivity(kind: String, permitted: Boolean): String {
        val setting = repository.snapshot().config.search
        val eligible = if (kind == "files") IndexAccessPolicy.files(setting, permitted) else IndexAccessPolicy.contacts(setting, permitted)
        if (!eligible) return "Background refresh: Off"
        if (kind == "contacts") (context.applicationContext as GroveApp).contactChanges.failure.value?.let { return it }
        IndexWork.failures.value?.get(kind)?.let { return "Background refresh: $it" }
        val work = workStates[kind]
        return "Background refresh: " + when (work?.state) {
            androidx.work.WorkInfo.State.RUNNING -> "Running"
            androidx.work.WorkInfo.State.ENQUEUED -> if (work.runAttemptCount > 0) "Waiting to retry" else "Queued"
            androidx.work.WorkInfo.State.BLOCKED -> "Waiting"
            androidx.work.WorkInfo.State.FAILED -> "Failed · live search available"
            else -> "Idle"
        }
    }
    fun retryIndex(kind: String): CommandFeedback = try {
        if (kind !in listOf("contacts", "files")) CommandFeedback(false, "Unknown index")
        else if (IndexWork.enqueue(context, kind, IndexRefreshCause.MANUAL)) CommandFeedback(true, "Index refresh requested")
        else CommandFeedback(false, "Enable search and indexing, and allow Android access first.")
    } catch (_: Exception) { CommandFeedback(false, "Index refresh unavailable") }
}

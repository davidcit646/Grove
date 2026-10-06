package tech.granet.grove

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Disposable, device-private snapshots. The caller must check permission and switches before use. */
internal object IndexCache {
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val VERSION = 1
    private val lock = Any()
    private val stateLock = Any()
    val metadataChanges = androidx.lifecycle.MutableLiveData<Map<String, IndexMetadata>>(emptyMap())
    private val metadata = mutableMapOf<String, IndexMetadata>()
    fun metadata(kind: String): IndexMetadata = synchronized(stateLock) {
        (metadata[kind] ?: IndexMetadata()).let { if (kind in invalid) it.copy(invalidated = true) else it }
    }
    private fun record(kind: String, value: IndexMetadata) = synchronized(stateLock) {
        metadata[kind] = value
        metadataChanges.postValue(metadata.toMap())
    }
    fun inspect(context: Context, kind: String): IndexMetadata {
        try {
            if (kind == "contacts") contacts(context) else files(context)
        } catch (_: Exception) { record(kind, IndexMetadata(IndexValidity.CORRUPT)) }
        return metadata(kind)
    }
    private fun verified(kind: String, value: JSONObject) {
        val writtenAt = value.getLong("writtenAt")
        val skipped = value.getInt("skipped")
        require(writtenAt > 0 && skipped >= 0) { "Invalid cache metadata" }
        record(kind, IndexMetadata(IndexValidity.AVAILABLE, writtenAt, skipped > 0 || value.getJSONArray("items").length() >= (if (kind == "files") 15_000 else 50_000)))
    }
    val changes = androidx.lifecycle.MutableLiveData<Map<String, Long>>(emptyMap())
    private val generations = mutableMapOf<String, Long>()
    private val invalid = mutableSetOf<String>()
    fun invalidate(kind: String) = synchronized(stateLock) { invalid.add(kind); metadataChanges.postValue(metadata.toMap()); Unit }
    fun invalidated(kind: String): Boolean = synchronized(stateLock) { kind in invalid }
    fun generation(kind: String): Long = synchronized(stateLock) { generations[kind] ?: 0L }
    private fun published(kind: String) = synchronized(stateLock) {
        generations[kind] = (generations[kind] ?: 0L) + 1L
        changes.postValue(generations.toMap())
    }

    data class Snapshot<T>(val items: List<T>, val skipped: Int, val writtenAt: Long)
    private fun target(context: Context, kind: String) = File(context.filesDir, "grove-$kind-index.json")

    fun clear(context: Context, kind: String) = synchronized(lock) {
        AtomicFile(target(context, kind)).delete()
        record(kind, IndexMetadata(IndexValidity.ABSENT))
        published(kind)
    }

    fun writeFiles(context: Context, result: FileIndex.ScanResult, allowed: () -> Boolean): Boolean {
        val rows = JSONArray()
        result.files.forEach { item -> rows.put(JSONObject().put("name", item.name.take(512))
            .put("path", item.file.absolutePath).put("mime", item.mime).put("category", item.category)) }
        return write(context, "files", rows, result.skippedDirectories + if (result.truncated) 1 else 0, allowed)
    }

    fun writeContacts(context: Context, scan: ContactIndex.ScanResult, allowed: () -> Boolean): Boolean {
        val rows = JSONArray()
        scan.contacts.forEach { item -> rows.put(JSONObject().put("id", item.id)
            .put("key", item.lookupKey.take(512)).put("name", item.name.take(512))) }
        return write(context, "contacts", rows, if (scan.truncated) 1 else 0, allowed)
    }

    private fun write(context: Context, kind: String, rows: JSONArray, skipped: Int, allowed: () -> Boolean): Boolean {
        val value = JSONObject().put("version", VERSION).put("writtenAt", System.currentTimeMillis())
            .put("skipped", skipped).put("items", rows)
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Index exceeds local size limit" }
        synchronized(lock) {
            if (!allowed()) return false
            val file = AtomicFile(target(context, kind))
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                if (!allowed()) { file.failWrite(stream); return false }
                file.finishWrite(stream)
                synchronized(stateLock) { invalid.remove(kind) }
                verified(kind, value)
                published(kind)
                return true
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    private fun read(context: Context, kind: String): JSONObject? = synchronized(lock) {
        val file = AtomicFile(target(context, kind))
        if (!file.baseFile.exists()) { record(kind, IndexMetadata(IndexValidity.ABSENT)); return null }
        require(file.baseFile.length() in 1..MAX_BYTES.toLong()) { "Invalid index size" }
        val value = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        require(value.getInt("version") == VERSION) { "Unknown index version" }
        value
    }

    fun files(context: Context): Snapshot<IndexedFile>? = synchronized(lock) { read(context, "files")?.let { value ->
        val rows = value.getJSONArray("items")
        require(rows.length() <= 15_000) { "Invalid file count" }
        Snapshot((0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            IndexedFile(row.getString("name"), row.getString("mime"), File(row.getString("path")), row.getString("category"))
        } }, maxOf(value.getInt("skipped"), if (rows.length() >= 15_000) 1 else 0), value.getLong("writtenAt")).also { verified("files", value) }
    }

    }
    fun contacts(context: Context): Snapshot<ContactIndex.Contact>? = synchronized(lock) { read(context, "contacts")?.let { value ->
        val rows = value.getJSONArray("items")
        require(rows.length() <= 50_000) { "Invalid contact count" }
        Snapshot((0 until rows.length()).map { i -> rows.getJSONObject(i).let { row ->
            require(row.getLong("id") > 0 && row.getString("key").isNotBlank() && row.getString("name").isNotBlank()) { "Invalid contact row" }
            ContactIndex.Contact(row.getLong("id"), row.getString("key"), row.getString("name"))
        } }, maxOf(value.getInt("skipped"), if (rows.length() >= 50_000) 1 else 0), value.getLong("writtenAt")).also { verified("contacts", value) }
    }
    }
}

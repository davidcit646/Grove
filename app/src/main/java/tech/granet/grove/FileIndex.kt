package tech.granet.grove

import android.webkit.MimeTypeMap
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale

data class IndexedFile(val name: String, val mime: String, val file: File, val category: String, private val preparedName: String? = null) {
    val searchName = preparedName ?: Search.normalize(name.take(512))
}

/** Scans accessible shared storage off the UI thread. Android owns access and file opening. */
object FileIndex {
    data class ScanResult(val files: List<IndexedFile>, val skippedDirectories: Int, val truncated: Boolean = false)

    fun scan(root: File, limit: Int = 15_000, shouldContinue: () -> Boolean = { true },
             openDirectory: (Path) -> DirectoryStream<Path> = Files::newDirectoryStream,
             maxDurationMs: Long = Long.MAX_VALUE): ScanResult {
        require(limit in 1..15_000)
        data class Raw(val name: String, val ext: String, val file: File)
        val found = ArrayList<Raw>()
        val queue = ArrayDeque<File>()
        if (!shouldContinue()) return ScanResult(emptyList(), 0, true)
        val rootPath = root.canonicalPath
        val visited = HashSet<String>()
        var scannedEntries = 0
        var skippedDirectories = 0
        val began = System.nanoTime()
        val durationNanos = if (maxDurationMs >= Long.MAX_VALUE / 1_000_000) Long.MAX_VALUE
            else maxDurationMs.coerceAtLeast(0) * 1_000_000
        fun expired() = System.nanoTime() - began >= durationNanos
        queue.add(root)
        while (shouldContinue() && !expired() && queue.isNotEmpty() && found.size < limit && visited.size < 10_000 && scannedEntries < 100_000) {
            val parent = queue.removeFirst()
            val path = try { parent.canonicalPath } catch (error: Exception) {
                if (parent == root) throw error
                skippedDirectories++
                continue
            }
            if ((path != rootPath && !path.startsWith("$rootPath${File.separator}")) || !visited.add(path)) continue
            // Iterate lazily: a single directory may have far more entries than
            // the index limit, and listFiles() allocates the entire listing.
            runCatching {
                openDirectory(parent.toPath()).use { entries ->
                    for (entryPath in entries) {
                        if (!shouldContinue() || expired()) break
                        if (++scannedEntries > 100_000) break
                        val entry = entryPath.toFile()
                        // Shared storage can contain links planted by other apps.
                        if (Files.isSymbolicLink(entryPath)) continue
                        if (entry.isDirectory) {
                            val protected = parent.name.equals("Android", true) && parent.parentFile?.canonicalPath == rootPath &&
                                (entry.name.equals("data", true) || entry.name.equals("obb", true))
                            if (!protected && queue.size < 10_000) queue.add(entry)
                            else if (!protected) skippedDirectories++
                        } else if (entry.isFile && found.size < limit) {
                            val canonical = entry.canonicalFile
                            if (canonical.path.startsWith("$rootPath${File.separator}"))
                                found.add(Raw(entry.name.take(512), entry.extension.lowercase(Locale.ROOT).take(128), canonical))
                        }
                        if (found.size >= limit) break
                    }
                }
            }.onFailure { error ->
                if (parent == root) throw error
                skippedDirectories++
            }
        }
        if (!shouldContinue()) return ScanResult(emptyList(), skippedDirectories, true)
        val truncated = queue.isNotEmpty() || found.size >= limit || scannedEntries >= 100_000 || expired()
        // One native call classifies every extension Grove knows about; anything
        // unknown falls back to Android's MimeTypeMap, exactly as before.
        val extensions = found.map { it.ext }.distinct()
        val table = extensions.zip(CoreBridge.classifyTable(extensions)).toMap()
        if (!shouldContinue()) return ScanResult(emptyList(), skippedDirectories, true)
        val normalized = Search.normalizeAll(found.map { it.name.take(512) }, shouldContinue)
        if (!shouldContinue()) return ScanResult(emptyList(), skippedDirectories, true)
        val files = found.mapIndexed { index, item ->
            val extra = table[item.ext]
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
                ?: extra?.first
                ?: "application/octet-stream"
            IndexedFile(item.name, mime, item.file, extra?.second ?: categoryOf(mime), normalized[index])
        }
        return ScanResult(files, skippedDirectories, truncated)
    }

    private fun categoryOf(mime: String): String = when {
        mime.startsWith("image/") -> "Images"
        mime.startsWith("video/") -> "Videos"
        mime.startsWith("audio/") -> "Audio"
        else -> "Documents"
    }
}

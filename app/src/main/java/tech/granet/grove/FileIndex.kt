package tech.granet.grove

import android.webkit.MimeTypeMap
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale

data class IndexedFile(val name: String, val mime: String, val file: File, val category: String) {
    val searchName = Search.normalize(name.take(512))
}

/** Scans accessible shared storage off the UI thread. Android owns access and file opening. */
object FileIndex {
    data class ScanResult(val files: List<IndexedFile>, val skippedDirectories: Int)

    fun scan(root: File, limit: Int = 15_000, shouldContinue: () -> Boolean = { true },
             openDirectory: (Path) -> DirectoryStream<Path> = Files::newDirectoryStream): ScanResult {
        data class Raw(val name: String, val ext: String, val file: File)
        val found = ArrayList<Raw>()
        val queue = ArrayDeque<File>()
        val rootPath = root.canonicalPath
        val visited = HashSet<String>()
        var scannedEntries = 0
        var skippedDirectories = 0
        queue.add(root)
        while (shouldContinue() && queue.isNotEmpty() && found.size < limit && visited.size < 10_000 && scannedEntries < 100_000) {
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
                        if (!shouldContinue()) break
                        if (++scannedEntries > 100_000) break
                        val entry = entryPath.toFile()
                        // Shared storage can contain links planted by other apps.
                        if (Files.isSymbolicLink(entryPath)) continue
                        if (entry.isDirectory) {
                            if (entry.name != "data" && entry.name != "obb" && queue.size < 10_000) queue.add(entry)
                        } else if (entry.isFile && found.size < limit) {
                            found.add(Raw(entry.name, entry.extension.lowercase(Locale.ROOT), entry))
                        }
                        if (found.size >= limit) break
                    }
                }
            }.onFailure { error ->
                if (parent == root) throw error
                skippedDirectories++
            }
        }
        if (!shouldContinue()) return ScanResult(emptyList(), skippedDirectories)
        // One native call classifies every extension Grove knows about; anything
        // unknown falls back to Android's MimeTypeMap, exactly as before.
        val table = CoreBridge.classifyTable(found.map { it.ext })
        val files = found.mapIndexed { index, item ->
            val extra = table[index]
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
                ?: extra?.first
                ?: "application/octet-stream"
            IndexedFile(item.name, mime, item.file, extra?.second ?: categoryOf(mime))
        }
        return ScanResult(files, skippedDirectories)
    }

    private fun categoryOf(mime: String): String = when {
        mime.startsWith("image/") -> "Images"
        mime.startsWith("video/") -> "Videos"
        mime.startsWith("audio/") -> "Audio"
        else -> "Documents"
    }
}

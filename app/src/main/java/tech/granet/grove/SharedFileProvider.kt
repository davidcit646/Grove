package tech.granet.grove

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.FileNotFoundException
import java.util.Locale

/** Read-only URI capability: authority is checked again when the recipient opens it. */
class SharedFileProvider : FileProvider() {
    private fun relative(uri: Uri): String {
        val app = context?.applicationContext as? GroveApp ?: throw FileNotFoundException("File unavailable")
        val allowed = try { app.settingsRepository.snapshot().config.search.files && Environment.isExternalStorageManager() }
            catch (_: Exception) { false }
        val parts = uri.pathSegments
        if (!allowed || uri.authority != "${app.packageName}.files" || parts.size < 2 || parts.first() != "shared" ||
            parts.drop(1).any { it.isEmpty() || it == "." || it == ".." || '/' in it || '\u0000' in it })
            throw FileNotFoundException("File unavailable")
        return parts.drop(1).joinToString("/")
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only file")
        val path = relative(uri)
        val root = Environment.getExternalStorageDirectory().canonicalPath
        val fd = CoreBridge.openShared(root, path) ?: throw FileNotFoundException("File unavailable")
        return try { ParcelFileDescriptor.adoptFd(fd) }
        catch (error: Exception) { CoreBridge.closeShared(fd); throw error }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val name = relative(uri).substringAfterLast('/')
        val size = openFile(uri, "r").use { it.statSize }
        val columns = (projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray(), 1).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) name else size })
        }
    }
    override fun getType(uri: Uri): String {
        val extension = relative(uri).substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only file")
}

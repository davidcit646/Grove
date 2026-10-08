package tech.granet.grove

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.nio.file.Files
import tech.granet.grove.ui.message

/** Rechecks shared storage access and canonical path at the moment a file is opened. */
internal class FileActions(
    private val activity: AppCompatActivity,
    private val enabled: () -> Boolean,
) {
    fun shareUri(file: File): Uri {
        require(enabled() && Environment.isExternalStorageManager()) { "File search is unavailable" }
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith("${root.path}${File.separator}") && file.exists() &&
            !Files.isSymbolicLink(file.toPath())) { "File is outside shared storage" }
        require(canonical.isFile && canonical.canRead()) { "File is unavailable" }
        val relative = canonical.relativeTo(root).invariantSeparatorsPath
        require(!relative.startsWith("Android/data/") && !relative.startsWith("Android/obb/")) {
            "Protected storage is unavailable"
        }
        var ancestor: File? = file.absoluteFile
        while (ancestor != null && ancestor != root) {
            require(!Files.isSymbolicLink(ancestor.toPath())) { "Symbolic links are unavailable" }
            ancestor = ancestor.parentFile
        }
        return FileProvider.getUriForFile(activity, "${activity.packageName}.files", canonical)
    }

    fun open(file: IndexedFile) {
        val uri = runCatching { shareUri(file.file) }
            .getOrElse { Log.w("Grove", "Cannot share indexed file", it); activity.message("Cannot open this file"); return }
        fun openAs(mime: String) = runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.isSuccess
        if (!openAs(file.mime) && !openAs("*/*")) {
            Log.w("Grove", "No app handles MIME type ${file.mime}")
            activity.message("No app can open this file")
        }
    }
}

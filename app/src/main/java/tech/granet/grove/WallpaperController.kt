package tech.granet.grove

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal enum class WallpaperApplyOutcome {
    APPLIED,
    PLATFORM_FAILED,
    LOCAL_SYNC_FAILED,
}

/** Loads, validates and applies local wallpaper sources away from launcher navigation code. */
internal class WallpaperController(
    private val activity: AppCompatActivity,
    private val message: (String) -> Unit,
) {
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun shutdown() { worker.shutdownNow() }
    init {
        runCatching {
            val wallpaperDir = File(activity.filesDir, "wallpapers")
            // Versions before #82 cached Commons downloads in private storage. Built-ins
            // are packaged now, so those stale network-era files have no authority.
            wallpaperDir.listFiles { file ->
                file.isFile && file.name.startsWith("commons-")
            }?.forEach(File::delete)

            // Recover an interrupted custom-image promotion before Home can read it.
            val committed = WallpaperArt.customFile(activity.filesDir)
            val backup = WallpaperArt.customBackupFile(activity.filesDir)
            if (!committed.exists() && backup.exists()) {
                if (!backup.renameTo(committed)) Log.w("Grove", "Could not restore custom wallpaper backup")
            } else if (committed.exists()) {
                backup.delete()
            }
        }.onFailure { Log.w("Grove", "Could not reconcile wallpaper storage", it) }
    }

    fun artwork(index: Int, preferPendingCustom: Boolean = false, maxWidth: Int = 1440, maxHeight: Int = 2560): Bitmap {
        val source = WallpaperArt.source(index) ?: error("Unknown wallpaper source")
        return when (source.kind) {
            WallpaperKind.GENERATED -> WallpaperArt.create(index, minOf(maxWidth, 1080), minOf(maxHeight, 2400))
            WallpaperKind.COMMONS -> WallpaperImages.decodeBundled(activity.resources, source.resourceId ?: error("Bundled wallpaper resource missing"), maxWidth, maxHeight)
                ?: error("Bundled wallpaper is unavailable")
            WallpaperKind.SOLID_BLACK ->
                Bitmap.createBitmap(minOf(maxWidth, 1080), minOf(maxHeight, 2400), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
            WallpaperKind.CUSTOM -> {
                val pending = WallpaperArt.customCandidateFile(activity.filesDir)
                val file = if (preferPendingCustom && pending.exists()) pending
                    else WallpaperArt.customFile(activity.filesDir)
                WallpaperImages.decode(file, maxWidth, maxHeight) ?: error("Custom wallpaper is unavailable")
            }
        }
    }

    /** Stage a user-picked image. The currently committed custom wallpaper is untouched until Android applies it. */
    fun importCustom(uri: Uri, done: (Boolean) -> Unit) {
        worker.execute {
            val candidate = WallpaperArt.customCandidateFile(activity.filesDir)
            val temp = File(candidate.parentFile, candidate.name + ".tmp")
            val ok = runCatching {
                val type = activity.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
                require(type.startsWith("image/")) { "Selected document is not an image" }
                candidate.parentFile?.mkdirs()
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        output.write(BoundedInput.read(input, MAX_CUSTOM_BYTES.toInt()))
                    }
                } ?: error("Cannot open selected image")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.absolutePath, bounds)
                validateCustomImage(type, temp.length(), bounds.outWidth, bounds.outHeight)
                val decoded = decode(temp) ?: error("Selected image is corrupt or has unsupported dimensions")
                decoded.recycle()
                candidate.delete()
                check(temp.renameTo(candidate)) { "Could not stage selected image" }
                true
            }.onFailure { Log.w("Grove", "Custom wallpaper import failed", it) }.getOrDefault(false)
            if (!ok) temp.delete()
            activity.runOnUiThread { if (!activity.isDestroyed) done(ok) }
        }
    }

    /** Decode and crop on the worker. The caller owns the returned bitmap. */
    fun background(index: Int, width: Int, height: Int, done: (Bitmap?, Pair<Int, Int>?) -> Unit) {
        worker.execute {
            val prepared = runCatching {
                // Home always reads the committed custom image, never an un-applied candidate.
                val source = artwork(index, preferPendingCustom = false)
                try {
                    centerCrop(source, width, height).also { if (it !== source) source.recycle() }
                } catch (error: Throwable) {
                    source.recycle()
                    throw error
                }
            }.onFailure { Log.w("Grove", "Wallpaper background unavailable", it) }.getOrNull()
            val colors = prepared?.let(ThemeColors::wallpaperButtonColors)
            activity.runOnUiThread {
                if (activity.isDestroyed) prepared?.recycle() else done(prepared, colors)
            }
        }
    }

    /** A small preview is decoded off the UI thread. Pending custom selection is allowed only inside the picker. */
    fun preview(index: Int, current: () -> Boolean = { true }, done: (Bitmap?) -> Unit) {
        worker.execute {
            if (!current() || activity.isDestroyed) return@execute
            val preview = runCatching {
                val full = PerformanceTrace.measure("Grove.wallpaper.decode") {
                    artwork(index, preferPendingCustom = true, maxWidth = 360, maxHeight = 800)
                }
                try {
                    PerformanceTrace.measure("Grove.wallpaper.thumbnail") { WallpaperImages.thumbnail(full, 360, 800) }.also { if (it !== full) full.recycle() }
                } catch (error: Throwable) { full.recycle(); throw error }
            }.onFailure { Log.w("Grove", "Wallpaper preview failed", it) }.getOrNull()
            activity.runOnUiThread {
                if (activity.isDestroyed || !current()) preview?.recycle() else done(preview)
            }
        }
    }

    fun apply(index: Int, which: Int, done: (WallpaperApplyOutcome) -> Unit) {
        worker.execute {
            var androidApplied = false
            val outcome = try {
                val source = artwork(index, preferPendingCustom = true)
                val bitmap = centerCrop(
                    source,
                    activity.resources.displayMetrics.widthPixels,
                    activity.resources.displayMetrics.heightPixels,
                )
                try {
                    WallpaperManager.getInstance(activity).setBitmap(bitmap, null, true, which)
                    androidApplied = true
                } finally {
                    bitmap.recycle()
                    if (source !== bitmap) source.recycle()
                }

                val selected = WallpaperArt.source(index)
                if (selected?.kind == WallpaperKind.CUSTOM &&
                    which and WallpaperManager.FLAG_SYSTEM != 0 &&
                    !promoteCandidate(
                        WallpaperArt.customFile(activity.filesDir),
                        WallpaperArt.customCandidateFile(activity.filesDir),
                        WallpaperArt.customBackupFile(activity.filesDir),
                    )
                ) WallpaperApplyOutcome.LOCAL_SYNC_FAILED
                else WallpaperApplyOutcome.APPLIED
            } catch (error: Exception) {
                Log.w("Grove", "Could not apply wallpaper", error)
                if (androidApplied) WallpaperApplyOutcome.LOCAL_SYNC_FAILED
                else WallpaperApplyOutcome.PLATFORM_FAILED
            }
            activity.runOnUiThread {
                if (!activity.isDestroyed) {
                    if (outcome == WallpaperApplyOutcome.APPLIED) message("Wallpaper applied")
                    done(outcome)
                }
            }
        }
    }

    companion object {
        private const val MAX_CUSTOM_BYTES = 20L * 1024 * 1024

        fun validateCustomImage(mime: String, bytes: Long, width: Int, height: Int) {
            PortablePolicy.bool("imageValid", org.json.JSONObject().put("mime", mime).put("bytes", bytes)
                .put("width", width).put("height", height))?.let { require(it) { "Invalid wallpaper image" }; return }
            require(mime.lowercase(Locale.ROOT).startsWith("image/")) { "Selected document is not an image" }
            require(bytes in 1..MAX_CUSTOM_BYTES) { "Wallpaper is too large" }
            require(width in 1..8192 && height in 1..8192) { "Invalid wallpaper dimensions" }
        }

        internal fun promoteCandidate(committed: File, candidate: File, backup: File): Boolean {
            if (!candidate.exists()) return committed.exists()
            backup.delete()
            if (committed.exists() && !committed.renameTo(backup)) return false
            if (candidate.renameTo(committed)) {
                backup.delete()
                return true
            }
            if (backup.exists()) backup.renameTo(committed)
            return false
        }

        fun decode(file: File): Bitmap? = WallpaperImages.decode(file)
        fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap =
            WallpaperImages.centerCrop(source, targetWidth, targetHeight)

    }
}

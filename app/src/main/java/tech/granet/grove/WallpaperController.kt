package tech.granet.grove

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService

/** Loads and applies artwork away from the launcher navigation code. */
internal class WallpaperController(
    private val activity: AppCompatActivity,
    private val worker: ExecutorService,
    private val message: (String) -> Unit,
) {
    private val maxDownloadBytes = 20L * 1024 * 1024

    private fun wallpaperConnection(start: URL): HttpURLConnection {
        var url = start
        repeat(5) {
            require(allowedWallpaperDestination(url)) { "Unexpected wallpaper destination" }
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "GroveLauncher wallpaper downloader")
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location") ?: error("Wallpaper redirect has no destination")
                    url = URL(url, location)
                } else {
                    require(status in 200..299) { "Wallpaper download failed (HTTP $status)" }
                    require(connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.startsWith("image/") == true) {
                        "Wallpaper response is not an image"
                    }
                    require(connection.contentLengthLong in -1L..maxDownloadBytes) { "Wallpaper is too large" }
                    return connection
                }
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        error("Too many wallpaper redirects")
    }

    fun artwork(index: Int): Bitmap = if (index >= 3) {
        WallpaperArt.cachedFile(activity.filesDir, index).takeIf { it.exists() }?.let(::decode)
            ?: WallpaperArt.create(0)
    } else WallpaperArt.create(index)

    /** Decode and crop on the worker. The caller owns the returned bitmap. */
    fun background(index: Int, width: Int, height: Int, done: (Bitmap?) -> Unit) {
        worker.execute {
            val prepared = runCatching {
                val source = artwork(index)
                try { centerCrop(source, width, height).also { if (it !== source) source.recycle() } }
                catch (error: Throwable) { source.recycle(); throw error }
            }.onFailure { Log.w("Grove", "Wallpaper background unavailable", it) }.getOrNull()
            activity.runOnUiThread {
                if (activity.isDestroyed) prepared?.recycle() else done(prepared)
            }
        }
    }

    fun download(index: Int, done: (Boolean) -> Unit) {
        val wallpaper = WallpaperArt.commons[index - 3]
        val target = WallpaperArt.cachedFile(activity.filesDir, index)
        worker.execute {
            val temp = File(target.parentFile, target.name + ".tmp")
            val ok = runCatching {
                if (target.exists() && decode(target)?.let { it.recycle(); true } == true) return@runCatching true
                target.delete()
                target.parentFile?.mkdirs()
                val encoded = java.net.URLEncoder.encode(wallpaper.fileName, "UTF-8").replace("+", "%20")
                val connection = wallpaperConnection(URL("https://commons.wikimedia.org/wiki/Special:FilePath/$encoded?width=1600"))
                try {
                    connection.inputStream.use { input -> FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= maxDownloadBytes) { "Wallpaper is too large" }
                            output.write(buffer, 0, count)
                        }
                    } }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temp.absolutePath, bounds)
                    require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "Invalid wallpaper dimensions" }
                    check(temp.renameTo(target)) { "Could not save wallpaper" }
                } finally { connection.disconnect() }
                true
            }.onFailure { Log.w("Grove", "Wallpaper download failed for ${wallpaper.fileName}", it) }
                .getOrDefault(false)
            temp.delete()
            activity.runOnUiThread { if (!activity.isDestroyed) done(ok) }
        }
    }

    /** A small preview is decoded off the UI thread. The caller owns and recycles it. */
    fun preview(index: Int, done: (Bitmap?) -> Unit) {
        fun decodePreview() {
            worker.execute {
                val preview = runCatching {
                    val full = if (index < 3) WallpaperArt.create(index)
                        else decode(WallpaperArt.cachedFile(activity.filesDir, index))
                    full?.let {
                        val cropped = centerCrop(it, 360, 800)
                        val scaled = Bitmap.createScaledBitmap(cropped, 360, 800, true)
                        if (cropped !== scaled && cropped !== it) cropped.recycle()
                        if (it !== scaled) it.recycle()
                        scaled
                    }
                }.onFailure { Log.w("Grove", "Wallpaper preview failed", it) }.getOrNull()
                activity.runOnUiThread {
                    if (activity.isDestroyed) preview?.recycle() else done(preview)
                }
            }
        }
        if (index < 3) decodePreview()
        else {
            val cached = WallpaperArt.cachedFile(activity.filesDir, index)
            worker.execute {
                val valid = runCatching { decode(cached)?.also { it.recycle() } != null }.getOrDefault(false)
                activity.runOnUiThread {
                    if (activity.isDestroyed) return@runOnUiThread
                    if (valid) decodePreview()
                    else download(index) { ok -> if (ok) decodePreview() else done(null) }
                }
            }
        }
    }

    fun apply(index: Int, which: Int, done: (Boolean) -> Unit) {
        worker.execute {
            runCatching {
                val source = if (index >= 3) decode(WallpaperArt.cachedFile(activity.filesDir, index))
                    ?: error("Wallpaper cache is missing") else WallpaperArt.create(index)
                val bitmap = centerCrop(source, activity.resources.displayMetrics.widthPixels,
                    activity.resources.displayMetrics.heightPixels)
                try { WallpaperManager.getInstance(activity).setBitmap(bitmap, null, true, which) }
                finally { bitmap.recycle(); if (source !== bitmap) source.recycle() }
            }.onSuccess { activity.runOnUiThread { if (!activity.isDestroyed) { message("Wallpaper applied"); done(true) } } }
                .onFailure {
                    Log.w("Grove", "Could not apply wallpaper", it)
                    activity.runOnUiThread { if (!activity.isDestroyed) { message("System wallpaper could not be changed"); done(false) } }
                }
        }
    }

    companion object {
        /** Wikimedia now redirects scaled images to its dedicated thumbnail host. */
        fun allowedWallpaperDestination(url: URL): Boolean =
            url.protocol.equals("https", true) &&
                url.host.lowercase(Locale.ROOT) in setOf(
                    "commons.wikimedia.org", "upload.wikimedia.org", "thumb.wikimedia.org"
                ) &&
                (url.port == -1 || url.port == 443) && url.userInfo == null

        fun decode(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) sample *= 2
            return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
            if (targetWidth <= 0 || targetHeight <= 0) return source
            val targetRatio = targetWidth.toFloat() / targetHeight
            val sourceRatio = source.width.toFloat() / source.height
            val cropWidth: Int
            val cropHeight: Int
            if (sourceRatio > targetRatio) {
                cropHeight = source.height
                cropWidth = (source.height * targetRatio).toInt().coerceIn(1, source.width)
            } else {
                cropWidth = source.width
                cropHeight = (source.width / targetRatio).toInt().coerceIn(1, source.height)
            }
            val left = (source.width - cropWidth) / 2
            val top = (source.height - cropHeight) / 2
            if (left == 0 && top == 0 && cropWidth == source.width && cropHeight == source.height) return source
            return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
        }
    }
}

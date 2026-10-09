package tech.granet.grove

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Android decoding and bitmap creation; bounded portable geometry lives in Rust. */
internal object WallpaperImages {
    private fun sample(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        PortablePolicy.int("imageSample", org.json.JSONObject().put("width", width).put("height", height)
            .put("maxWidth", maxWidth).put("maxHeight", maxHeight), 1..8192)?.let { return it }
        var sample = 1
        while (width / sample > maxWidth || height / sample > maxHeight) sample *= 2
        return sample
    }
    fun decodeBundled(resources: Resources, resourceId: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resourceId, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        return BitmapFactory.decodeResource(resources, resourceId, BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        })
    }
    fun decode(file: File, maxWidth: Int = 1440, maxHeight: Int = 2560): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        })
    }
    fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (targetWidth <= 0 || targetHeight <= 0) return source
        val native = PortablePolicy.value("crop", org.json.JSONObject().put("width", source.width)
            .put("height", source.height).put("targetWidth", targetWidth).put("targetHeight", targetHeight)) as? org.json.JSONObject
        val targetRatio = targetWidth.toFloat() / targetHeight
        val sourceRatio = source.width.toFloat() / source.height
        val cropWidth = native?.optInt("width", -1)?.takeIf { it in 1..source.width }
            ?: if (sourceRatio > targetRatio) (source.height * targetRatio).toInt().coerceIn(1, source.width) else source.width
        val cropHeight = native?.optInt("height", -1)?.takeIf { it in 1..source.height }
            ?: if (sourceRatio > targetRatio) source.height else (source.width / targetRatio).toInt().coerceIn(1, source.height)
        val left = (source.width - cropWidth) / 2; val top = (source.height - cropHeight) / 2
        if (left == 0 && top == 0 && cropWidth == source.width && cropHeight == source.height) return source
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }
}

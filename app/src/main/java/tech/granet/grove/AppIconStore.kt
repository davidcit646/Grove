package tech.granet.grove

import android.content.ComponentName
import android.graphics.Bitmap

/**
 * UI-thread-owned bitmap cache. Views may still hold a bitmap after an entry is
 * removed, so this store drops references but never recycles an in-use bitmap.
 * It owns no Activity, View, or Context.
 */
object AppIconStore {
    private val icons = HashMap<String, Bitmap>()
    private var size = 0

    fun useSize(pixels: Int) {
        if (size != pixels) {
            icons.clear()
            size = pixels
        }
    }

    operator fun get(key: String): Bitmap? = icons[key]

    fun snapshotExcluding(packageName: String?): Map<String, Bitmap> =
        if (packageName == null) HashMap(icons)
        else icons.filterKeys {
            ComponentName.unflattenFromString(it)?.packageName != packageName
        }

    fun replace(next: Map<String, Bitmap>) {
        icons.clear()
        icons.putAll(next)
    }

    fun putAll(batch: Map<String, Bitmap>) {
        icons.putAll(batch)
    }

    fun clear() {
        icons.clear()
    }
}

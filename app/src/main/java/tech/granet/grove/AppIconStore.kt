package tech.granet.grove

import android.content.ComponentName
import android.graphics.Bitmap

/** Size-keyed cache primitive; changing rendered icon size invalidates every prior entry. */
internal class SizedCache<V> {
    private val values = HashMap<String, V>()
    private var size = 0

    fun useSize(pixels: Int) {
        if (size != pixels) {
            values.clear()
            size = pixels
        }
    }

    operator fun get(key: String): V? = values[key]
    fun snapshot(): Map<String, V> = HashMap(values)
    fun replace(next: Map<String, V>) { values.clear(); values.putAll(next) }
    fun putAll(next: Map<String, V>) { values.putAll(next) }
    fun clear() { values.clear() }
}

/**
 * UI-thread-owned bitmap cache. Views may still hold a bitmap after an entry is
 * removed, so this store drops references but never recycles an in-use bitmap.
 * It owns no Activity, View, or Context.
 */
object AppIconStore {
    private val icons = SizedCache<Bitmap>()

    fun useSize(pixels: Int) = icons.useSize(pixels)

    operator fun get(key: String): Bitmap? = icons[key]

    fun snapshotExcluding(packageName: String?): Map<String, Bitmap> {
        val snapshot = icons.snapshot()
        return if (packageName == null) snapshot
        else snapshot.filterKeys {
            ComponentName.unflattenFromString(it)?.packageName != packageName
        }
    }

    fun replace(next: Map<String, Bitmap>) = icons.replace(next)

    fun putAll(batch: Map<String, Bitmap>) = icons.putAll(batch)

    fun clear() = icons.clear()
}

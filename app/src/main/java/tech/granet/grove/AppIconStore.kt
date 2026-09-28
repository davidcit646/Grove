package tech.granet.grove

import android.graphics.Bitmap

/** Process-owned, UI-thread-confined cache. No Activity, View, or Context references. */
object AppIconStore {
    val icons = mutableMapOf<String, Bitmap>()
    private var size = 0
    fun useSize(pixels: Int) {
        if (size != pixels) { icons.clear(); size = pixels }
    }
}

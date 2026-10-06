package tech.granet.grove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.app.WallpaperColors
import android.util.TypedValue

/** Colors for interface glyphs and surfaces that must follow system light/dark mode. */
internal object ThemeColors {
    fun icon(context: Context): Int = resolve(context, com.google.android.material.R.attr.colorOnSurface)
    fun iconSurface(context: Context): Int = resolve(context, com.google.android.material.R.attr.colorSurfaceVariant)
    fun buttonSurface(context: Context): Int = resolve(context, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    fun onButtonSurface(context: Context): Int = resolve(context, com.google.android.material.R.attr.colorOnSurface)

    fun wallpaperButtonColors(bitmap: Bitmap): Pair<Int, Int> {
        return runCatching {
            val background = WallpaperColors.fromBitmap(bitmap).primaryColor.toArgb()
            val foreground = if (Color.luminance(background) > 0.179f) Color.BLACK else Color.WHITE
            background to foreground
        }.getOrDefault(0xff416e60.toInt() to Color.WHITE)
    }

    private fun resolve(context: Context, attribute: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) value.data else 0xff202020.toInt()
    }
}

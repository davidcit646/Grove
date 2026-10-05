package tech.granet.grove

import android.graphics.*
import java.io.File

internal enum class WallpaperKind { GENERATED, COMMONS, SOLID_BLACK, CUSTOM }

internal data class WallpaperSource(
    val id: String,
    val legacyIndex: Int,
    val kind: WallpaperKind,
    val title: String,
    val author: String,
    val license: String,
    val sourcePage: String? = null,
    val licenseUrl: String? = null,
    val changes: String? = null,
    val resourceId: Int? = null,
)

data class CommonsWallpaper(
    val color: String,
    val title: String,
    val fileName: String,
    val author: String,
    val license: String = "CC0 1.0",
    val licenseUrl: String = "https://creativecommons.org/publicdomain/zero/1.0/",
    val changes: String? = null,
) {
    val sourcePage: String get() = "https://commons.wikimedia.org/wiki/File:${UriCompat.encodeTitle(fileName)}"
}

private object UriCompat {
    fun encodeTitle(title: String) = java.net.URLEncoder.encode(title.replace(' ', '_'), Charsets.UTF_8.name())
}

/** Bundled original gradients plus a curated set of freely licensed Wikimedia Commons images. */
internal object WallpaperArt {
    val commons = listOf(
        CommonsWallpaper("Red", "Flower", "Red Flower red.jpg", "E.Prabha"),
        CommonsWallpaper("Orange", "Sunset landscape", "Landscape-sunset-sun-orange (24300255306).jpg", "www.Pixel.la Free Stock Photos"),
        CommonsWallpaper("Yellow", "Flower", "Yellow flower.png", "SurendharKandasami"),
        CommonsWallpaper("Green", "Hills and trees", "Green Landscape with Hills and Trees.jpg", "Baap8969"),
        CommonsWallpaper("Blue", "Flower", "A Blue Flower.jpg", "Iurie Nistor"),
        CommonsWallpaper("Purple", "Flower", "Purple flower nature.jpg", "Reshmamohamed"),
        CommonsWallpaper("Pink", "Paper flower", "Pink Paper flower.jpg", "By atmika"),
        CommonsWallpaper("Brown", "Sand dunes", "Sand dunes landscape before sunset, Lençóis Maranhenses.jpg", "Gerda Arendt"),
        CommonsWallpaper("Gray", "Granite texture", "Grey granite boulder seamless stone surface texture.jpg", "Sisters.seamless"),
        CommonsWallpaper(
            "Black & white",
            "Abstract landscape",
            "Black ^ white quasi-abstract landscape - Flickr - rossomoto.jpg",
            "rossomoto",
            "CC BY 2.0",
            "https://creativecommons.org/licenses/by/2.0/",
            "Bundled copy resized and optimized for Grove.",
        ),
    )

    val sources: List<WallpaperSource> =
        listOf(
            WallpaperSource("grove-fern", 0, WallpaperKind.GENERATED, "Fern · abstract", "Grove", "Apache 2.0"),
            WallpaperSource("grove-ember", 1, WallpaperKind.GENERATED, "Ember · mountain", "Grove", "Apache 2.0"),
            WallpaperSource("grove-dusk", 2, WallpaperKind.GENERATED, "Dusk · mountain", "Grove", "Apache 2.0"),
        ) + commons.zip(
            listOf(
                R.drawable.wallpaper_red,
                R.drawable.wallpaper_orange,
                R.drawable.wallpaper_yellow,
                R.drawable.wallpaper_green,
                R.drawable.wallpaper_blue,
                R.drawable.wallpaper_purple,
                R.drawable.wallpaper_pink,
                R.drawable.wallpaper_brown,
                R.drawable.wallpaper_gray,
                R.drawable.wallpaper_black_white,
            )
        ).mapIndexed { offset, (item, resourceId) ->
            WallpaperSource(
                "commons-${offset}",
                offset + 3,
                WallpaperKind.COMMONS,
                "${item.color} · ${item.title}",
                item.author,
                item.license,
                item.sourcePage,
                item.licenseUrl,
                item.changes,
                resourceId,
            )
        } + listOf(
            WallpaperSource("solid-black", 13, WallpaperKind.SOLID_BLACK, "Solid black", "Grove", "Apache 2.0"),
            WallpaperSource("custom-image", 14, WallpaperKind.CUSTOM, "Your photo or file", "You", "Local image"),
        )

    fun source(index: Int): WallpaperSource? = sources.firstOrNull { it.legacyIndex == index }
    fun indexForId(id: String): Int? = sources.firstOrNull { it.id == id }?.legacyIndex
    fun customFile(filesDir: File) = File(filesDir, "wallpapers/custom-image")
    fun customCandidateFile(filesDir: File) = File(filesDir, "wallpapers/custom-image.pending")
    fun customBackupFile(filesDir: File) = File(filesDir, "wallpapers/custom-image.backup")

    private const val ART_WIDTH = 1080
    private const val ART_HEIGHT = 2400

    fun create(style: Int): Bitmap {
        // The generative art lives in the Rust core now; the Canvas painter
        // below stays as the fallback when the native library is absent.
        CoreBridge.renderWallpaper(style, ART_WIDTH, ART_HEIGHT)?.let { pixels ->
            return Bitmap.createBitmap(pixels, 0, ART_WIDTH, ART_WIDTH, ART_HEIGHT, Bitmap.Config.ARGB_8888)
        }
        return createCanvas(style)
    }

    private fun createCanvas(style: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ART_WIDTH, ART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = when(style) {
            1 -> intArrayOf(0xffe5b886.toInt(), 0xffa25440.toInt(), 0xff263c37.toInt())
            2 -> intArrayOf(0xff666788.toInt(), 0xffc19a98.toInt(), 0xff18343b.toInt())
            else -> intArrayOf(0xff9ab095.toInt(), 0xff416e60.toInt(), 0xff142f30.toInt())
        }
        paint.shader = LinearGradient(0f, 0f, ART_WIDTH.toFloat(), ART_HEIGHT.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, ART_WIDTH.toFloat(), ART_HEIGHT.toFloat(), paint); paint.shader = null
        paint.color = 0x44fff1cc; canvas.drawCircle(800f, 740f, 180f, paint)
        if (style == 0) {
            for (i in 0..5) {
                paint.color = Color.argb(45 + i * 20, 14, 50, 40)
                canvas.drawOval(-500f + i * 100f, 1100f + i * 150f, 1600f, 2900f + i * 130f, paint)
            }
        } else {
            for (i in 0..3) {
                paint.color = Color.rgb(55 - i * 10, 78 - i * 12, 76 - i * 10)
                val y = 1150f + i * 270f
                val path = Path().apply { moveTo(-100f, y + 400); lineTo(260f, y + 160); lineTo(570f, y - 180); lineTo(840f, y + 140); lineTo(1180f, y + 50); lineTo(1180f, 2400f); lineTo(-100f, 2400f); close() }
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}

package tech.granet.grove

import android.graphics.*

internal object WallpaperRenderer {
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

package tech.granet.grove

/** Reject malformed JNI payloads before they reach app state or Bitmap creation. */
internal object NativeResults {
    fun search(order: IntArray, labelCount: Int, limit: Int): IntArray {
        require(order.size <= limit && order.distinct().size == order.size &&
            order.all { it in 0 until labelCount }) { "Malformed native search order" }
        return order
    }

    fun mime(packed: Array<String>, expected: List<Pair<String, String>?>): List<Pair<String, String>?> {
        require(packed.size == expected.size) { "Malformed native MIME length" }
        val decoded = packed.map { value ->
            if (value.isEmpty()) null
            else value.split('|').let { parts ->
                require(parts.size == 2 && parts[0].contains('/') && parts[1].isNotBlank()) {
                    "Malformed native MIME result"
                }
                parts[0] to parts[1]
            }
        }
        require(decoded == expected) { "Native MIME table differs from Kotlin" }
        return decoded
    }

    fun wallpaper(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height) {
            "Malformed native wallpaper"
        }
        return pixels
    }
}

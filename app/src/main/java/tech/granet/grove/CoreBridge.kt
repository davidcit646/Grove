package tech.granet.grove

import java.util.PriorityQueue

/**
 * One coarse JNI call per query, scan, or wallpaper render. The native library
 * owns scoring, top-K selection, Grove's extension table, wallpaper art, and
 * config validation; Kotlin fallbacks below keep the app working when the
 * library is absent (and are what unit tests exercise on the JVM).
 */
internal object CoreBridge {
    private val loaded = runCatching { System.loadLibrary("grove_core"); true }.getOrDefault(false)

    private external fun searchNative(labels: Array<String>, query: String, limit: Int): IntArray
    private external fun classifyNative(extensions: Array<String>): Array<String>
    private external fun renderWallpaperNative(style: Int, width: Int, height: Int): IntArray
    private external fun configErrorNative(json: String): String

    /** Winning label indices in final order: score descending, index ascending. */
    fun searchOrder(labels: List<String>, query: Search.Query, limit: Int): IntArray =
        searchOrder(labels.toTypedArray(), query, limit)

    fun searchOrder(labels: Array<String>, query: Search.Query, limit: Int): IntArray {
        if (labels.isEmpty() || limit <= 0) return IntArray(0)
        if (loaded) runCatching {
            searchNative(labels, query.text, limit)
                .also { order -> require(order.all { it in labels.indices }) }
        }.getOrNull()?.let { return it }
        return fallbackOrder(labels, query, limit)
    }

    private fun fallbackOrder(labels: Array<String>, query: Search.Query, limit: Int): IntArray {
        val scores = labels.map { Search.scoreNormalized(it, query) }
        // Keep only the best few rows instead of sorting the entire file
        // index every time the user types a character.
        val best = PriorityQueue<Int>(compareBy<Int> { scores[it] }.thenByDescending { it })
        for (index in labels.indices) {
            if (scores[index] < 0) continue
            if (best.size < limit) best.add(index)
            else {
                val worst = best.peek() ?: continue
                if (scores[index] > scores[worst] ||
                    (scores[index] == scores[worst] && index < worst)) {
                    best.remove(); best.add(index)
                }
            }
        }
        return best.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it }).toIntArray()
    }

    /**
     * Grove's own extension table: (mime, category) per extension, or null when
     * unknown so callers fall back to Android's MimeTypeMap.
     */
    fun classifyTable(extensions: List<String>): List<Pair<String, String>?> {
        if (extensions.isEmpty()) return emptyList()
        if (loaded) runCatching {
            classifyNative(extensions.toTypedArray()).map { packed ->
                if (packed.isEmpty()) null
                else packed.split('|').let { parts ->
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
            }.also { require(it.size == extensions.size) }
        }.getOrNull()?.let { return it }
        return extensions.map { null }
    }

    /** ARGB pixels (row-major) for a generative wallpaper style, or null. */
    fun renderWallpaper(style: Int, width: Int, height: Int): IntArray? {
        if (!loaded || width <= 0 || height <= 0) return null
        return runCatching {
            renderWallpaperNative(style, width, height)
                .also { require(it.size == width * height) }
        }.getOrNull()
    }

    /** Kotlin's parser stays authoritative while native config validation is migrated. */
    fun configProblem(json: String): String? = if (loaded) {
        runCatching { configErrorNative(json).ifEmpty { null } }.getOrNull()
    } else null
}

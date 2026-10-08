package tech.granet.grove

import android.util.Log
import java.util.PriorityQueue

/**
 * One coarse JNI call per query, scan, or wallpaper render. The native library
 * owns scoring, top-K selection, Grove's extension table, wallpaper art, and
 * config validation; Kotlin fallbacks below keep the app working when the
 * library is absent (and are what unit tests exercise on the JVM).
 */
internal object CoreBridge {
    private val failures = NativeFailureReporter { operation, error ->
        runCatching { Log.w("Grove", "Native $operation unavailable; using Kotlin fallback", error) }
    }
    private val loaded = runCatching { System.loadLibrary("grove_core"); true }
        .onFailure { failures.failed("load", it) }.getOrDefault(false)

    private fun <T> native(operation: String, call: () -> T): T? = try { call() }
        catch (error: Exception) { failures.failed(operation, error); null }
        catch (error: LinkageError) { failures.failed(operation, error); null }

    private external fun searchNative(labels: Array<String>, query: String, limit: Int): IntArray
    private external fun classifyNative(extensions: Array<String>): Array<String>
    private external fun renderWallpaperNative(style: Int, width: Int, height: Int): IntArray
    private external fun policyNative(json: String): String

    /** Winning label indices in final order: score descending, index ascending. */
    fun searchOrder(labels: List<String>, query: Search.Query, limit: Int): IntArray =
        searchOrder(labels.toTypedArray(), query, limit)

    fun searchOrder(labels: Array<String>, query: Search.Query, limit: Int): IntArray {
        if (labels.isEmpty() || limit <= 0) return IntArray(0)
        if (loaded) native("search") {
            NativeResults.search(searchNative(labels, query.text, limit), labels.size, limit)
        }?.let { return it }
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
        val fallback = extensions.map(::extensionOverride)
        if (loaded) native("mime") {
            NativeResults.mime(classifyNative(extensions.toTypedArray()), fallback)
        }?.let { return it }
        return fallback
    }

    internal fun extensionOverride(ext: String): Pair<String, String>? = when (ext) {
        "m4a" -> "audio/mp4" to "Audio"
        "csv" -> "text/csv" to "Documents"
        "mkv" -> "video/x-matroska" to "Videos"
        "opus" -> "audio/ogg" to "Audio"
        "weba" -> "audio/webm" to "Audio"
        else -> null
    }

    /** ARGB pixels (row-major) for a generative wallpaper style, or null. */
    fun renderWallpaper(style: Int, width: Int, height: Int): IntArray? {
        if (!loaded || width <= 0 || height <= 0 || width.toLong() * height > 16_000_000) return null
        return native("wallpaper") {
            NativeResults.wallpaper(renderWallpaperNative(style, width, height), width, height)
        }
    }

    /** Versioned portable policy envelope. Null means native unavailable, never invalid input. */
    internal fun portable(operation: String, args: org.json.JSONObject): org.json.JSONObject? {
        if (!loaded) return null
        val input = org.json.JSONObject().put("op", operation).put("args", args).toString()
        if (input.toByteArray(Charsets.UTF_8).size > 196_608) return null
        return native(operation) {
            val text = policyNative(input)
            require(text.toByteArray(Charsets.UTF_8).size <= 196_608) { "Portable output too large" }
            org.json.JSONObject(text).also {
                require(it.getInt("version") == 1 && (it.has("value") xor it.has("error"))) { "Malformed native policy response" }
            }
        }
    }
}

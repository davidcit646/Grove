package tech.granet.grove

import java.text.Normalizer
import java.util.Locale

/** Normalize app labels once at discovery; prepare each query once per text change. */
object Search {
    private val marks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")
    data class Query(val text: String, val terms: List<String>)
    fun normalize(value: String): String {
        if (value.length <= 4096) (PortablePolicy.value("normalize", org.json.JSONObject().put("text", value)) as? String)
            ?.takeIf { it.length <= 8192 }?.let { return it }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT).trim()
    }
    fun prepare(value: String): Query {
        val text = normalize(value.take(256))
        return Query(text, text.split(whitespace).filter { it.isNotEmpty() })
    }
    fun score(label: String, query: String): Int = scoreNormalized(normalize(label), prepare(query))
    fun scoreNormalized(label: String, query: Query): Int {
        if (query.terms.isEmpty()) return 0
        val words = label.split(whitespace)
        if (!query.terms.all { term -> label.contains(term) ||
                (term.length >= 3 && words.any { word -> word.length >= 3 &&
                    editDistanceAtMost(word, term, if (term.length >= 6) 2 else 1) }) }) return -1
        return if (label == query.text) 3 else if (label.startsWith(query.text)) 2 else 1
    }

    private fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
        if (a.length > 64 || b.length > 64) return false
        if (kotlin.math.abs(a.length - b.length) > max) return false
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(
                current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous[b.length] <= max
    }
}

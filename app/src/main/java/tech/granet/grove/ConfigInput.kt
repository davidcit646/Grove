package tech.granet.grove

/** Bounded strict JSON guard for the recovery decoder and before crossing JNI. */
internal object ConfigInput {
    fun validate(text: String) {
        require(text.length <= 65_536 && text.toByteArray(Charsets.UTF_8).size <= 65_536) { "Configuration exceeds 64 KB" }
        Parser(text).document()
    }
    private class Parser(val text: String) {
        var index = 0
        fun fail(): Nothing = throw IllegalArgumentException("Invalid configuration JSON")
        fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        fun take(c: Char): Boolean { if (text.getOrNull(index) != c) return false; index++; return true }
        fun document() { value(0); whitespace(); if (index != text.length) fail() }
        fun value(depth: Int) {
            if (depth > 64) throw IllegalArgumentException("Configuration is nested too deeply")
            whitespace()
            if (depth >= 64 && text.getOrNull(index) in listOf('{', '[')) throw IllegalArgumentException("Configuration is nested too deeply")
            when (text.getOrNull(index)) {
                '{' -> {
                    index++; whitespace()
                    if (take('}')) return
                    while (true) {
                        whitespace(); string(); whitespace(); if (!take(':')) fail()
                        value(depth + 1); whitespace(); if (take('}')) return; if (!take(',')) fail()
                    }
                }
                '[' -> {
                    index++; whitespace(); if (take(']')) return
                    while (true) { value(depth + 1); whitespace(); if (take(']')) return; if (!take(',')) fail() }
                }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-', in '0'..'9' -> number()
                else -> fail()
            }
        }
        fun literal(value: String) { if (!text.startsWith(value, index)) fail(); index += value.length }
        fun number() {
            val start = index
            take('-')
            if (!take('0')) { if (text.getOrNull(index) !in '1'..'9') fail(); digits() }
            if (take('.')) { val begin = index; digits(); if (begin == index) fail() }
            if (take('e') || take('E')) { if (!take('+')) take('-'); val begin = index; digits(); if (begin == index) fail() }
            if (text.substring(start, index).toDoubleOrNull()?.isFinite() != true) fail()
        }
        fun digits() { while (text.getOrNull(index) in '0'..'9') index++ }
        fun string() {
            if (!take('"')) fail()
            var highSurrogate = false
            while (index < text.length) {
                var c = text[index++]
                if (c == '"') { if (highSurrogate) fail(); return }
                if (c < ' ') fail()
                if (c == '\\') {
                    c = text.getOrNull(index++) ?: fail()
                    c = when (c) {
                        '"', '\\', '/' -> c
                        'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            if (index + 4 > text.length) fail()
                            val hex = text.substring(index, index + 4)
                            if (hex.any { it !in "0123456789abcdefABCDEF" }) fail()
                            index += 4; hex.toInt(16).toChar()
                        }
                        else -> fail()
                    }
                }
                if (highSurrogate && !c.isLowSurrogate()) fail()
                if (!highSurrogate && c.isLowSurrogate()) fail()
                highSurrogate = c.isHighSurrogate()
            }
            fail()
        }
    }
}

package tech.granet.grove

/** App-private tutorial state, independent of configuration and all other tutorials. */
internal class SearchTutorialState(page: Int = 0, var sessionSuppressed: Boolean = false) {
    var page = page.coerceIn(0, 2); private set
    fun forward(): Boolean {
        PortablePolicy.ruleInt("tutorialPage", 0..2, "page" to page, "delta" to 1)?.let { next ->
            val completed = page == 2; page = next; return completed
        }
        if (page == 2) return true
        page++
        return false
    }
    fun back(): Boolean {
        PortablePolicy.ruleInt("tutorialPage", 0..2, "page" to page, "delta" to -1)?.let { next ->
            val moved = next != page; page = next; return moved
        }
        if (page == 0) return false
        page--
        return true
    }
    fun completed(persist: () -> Boolean): Boolean {
        if (page != 2) return false
        val saved = persist()
        sessionSuppressed = !saved
        return saved
    }
    companion object {
        const val VERSION = 1
        fun shouldShow(savedVersion: Int, replay: Boolean, suppressed: Boolean) =
            PortablePolicy.ruleBool("tutorialShow", "suppressed" to suppressed, "replay" to replay, "saved" to savedVersion)
                ?: (!suppressed && (replay || savedVersion < VERSION))
    }
}

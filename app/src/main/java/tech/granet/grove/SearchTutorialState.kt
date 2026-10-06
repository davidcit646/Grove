package tech.granet.grove

/** App-private tutorial state, independent of configuration and all other tutorials. */
internal class SearchTutorialState(page: Int = 0, var sessionSuppressed: Boolean = false) {
    var page = page.coerceIn(0, 2); private set
    fun forward(): Boolean {
        if (page == 2) return true
        page++
        return false
    }
    fun back(): Boolean {
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
            !suppressed && (replay || savedVersion < VERSION)
    }
}

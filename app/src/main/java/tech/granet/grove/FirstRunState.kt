package tech.granet.grove

/** Setup answers remain provisional until Finish returns one validated Config. */
internal class FirstRunState(private val initial: Config, private val availableApps: List<String>) {
    var page = 0
        private set
    var gestures = initial.gestures
    var home = initial.homeScreen
    var search = initial.search
    val pins = initial.favorites.toMutableSet()
    var practicedUp = false
    var practicedDown = false
    var practicedHold = false

    fun pages(): List<Int> {
        val native = PortablePolicy.rule("setupPages", "down" to gestures.swipeDownSearch, "up" to gestures.swipeUpAppDrawer) as? org.json.JSONArray
        if (native != null) {
            val pages = (0 until native.length()).map { native.getInt(it) }
            if (pages == listOf(0,1,2,3,4,5,6,7) || pages == listOf(0,1,3,4,5,6,7)) return pages
        }
        return if (gestures.swipeDownSearch || gestures.swipeUpAppDrawer)
        listOf(0, 1, 2, 3, 4, 5, 6, 7) else listOf(0, 1, 3, 4, 5, 6, 7)
    }

    private fun nativeStep(back: Boolean): org.json.JSONObject? = PortablePolicy.rule("setupStep",
        "page" to page, "back" to back, "down" to gestures.swipeDownSearch, "up" to gestures.swipeUpAppDrawer) as? org.json.JSONObject
    fun back(): Boolean {
        nativeStep(true)?.let { step ->
            val next = step.optInt("page", -1)
            if (next in 0..7) { val moved = next != page; page = next; return moved }
        }
        val visible = pages()
        val current = visible.indexOf(page)
        if (current <= 0) return false
        page = visible[current - 1]
        return true
    }

    /** Returns a config only on Finish. Denied permissions remove the unavailable source. */
    fun next(contactsGranted: Boolean, filesGranted: Boolean): Config? {
        if (page == 6 && !contactsGranted) search = search.copy(contacts = false)
        if (page == 7) {
            if (!filesGranted) search = search.copy(files = false)
            return initial.copy(gestures = gestures, homeScreen = home, search = search,
                favorites = preparedPins())
        }
        page = nativeStep(false)?.optInt("page", -1)?.takeIf { it in 0..7 } ?: pages().first { it > page }
        return null
    }

    private fun preparedPins(): List<String> {
        val result = PortablePolicy.rule("setupFavorites", "available" to org.json.JSONArray(availableApps), "pins" to org.json.JSONArray(pins)) as? org.json.JSONArray
        if (result != null) {
            val rows = (0 until result.length()).map(result::getString)
            if (rows.all { it in availableApps && it in pins } && rows.distinct().size == rows.size) return rows
        }
        return availableApps.filter(pins::contains)
    }

    fun snapshot(): Config = initial.copy(gestures = gestures, homeScreen = home, search = search,
        favorites = preparedPins())

    fun restore(config: Config, restoredPage: Int, up: Boolean, down: Boolean, hold: Boolean) {
        gestures = config.gestures; home = config.homeScreen; search = config.search
        pins.clear(); pins.addAll(config.favorites.filter(availableApps::contains))
        page = restoredPage.takeIf { it in pages() } ?: 0
        practicedUp = up; practicedDown = down; practicedHold = hold
    }

    fun togglePin(key: String, checked: Boolean): Boolean {
        if ((PortablePolicy.ruleBool("setupPin", "checked" to checked, "contained" to (key in pins), "count" to pins.size)
            ?: (!checked || key in pins || pins.size < 12)) == false) return false
        if (checked) pins.add(key) else pins.remove(key)
        return true
    }
}

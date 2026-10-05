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

    fun pages(): List<Int> = if (gestures.swipeDownSearch || gestures.swipeUpAppDrawer)
        listOf(0, 1, 2, 3, 4, 5, 6, 7) else listOf(0, 1, 3, 4, 5, 6, 7)

    fun back(): Boolean {
        val visible = pages()
        val current = visible.indexOf(page)
        if (current <= 0) return false
        page = visible[current - 1]
        return true
    }

    /** Returns a config only on Finish. Denied permissions remove the unavailable source. */
    fun next(contactsGranted: Boolean, filesGranted: Boolean): Config? {
        if (page == 6 && !contactsGranted) search = search.copy(contacts = false, contactIndexing = false)
        if (page == 7) {
            if (!filesGranted) search = search.copy(files = false, fileIndexing = false)
            return initial.copy(gestures = gestures, homeScreen = home, search = search,
                favorites = availableApps.filter(pins::contains))
        }
        page = pages().first { it > page }
        return null
    }

    fun togglePin(key: String, checked: Boolean): Boolean {
        if (checked && key !in pins && pins.size >= 12) return false
        if (checked) pins.add(key) else pins.remove(key)
        return true
    }
}

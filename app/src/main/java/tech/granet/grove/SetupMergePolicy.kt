package tech.granet.grove

/** Setup edits only its lanes. A concurrent edit to the same lane requires a new review. */
internal object SetupMergePolicy {
    fun merge(base: Config, draft: Config, current: Config): Config? {
        fun <T> conflict(old: T, proposed: T, now: T) = proposed != old && now != old && now != proposed
        if (conflict(base.gestures, draft.gestures, current.gestures) ||
            conflict(base.homeScreen, draft.homeScreen, current.homeScreen) ||
            conflict(base.search, draft.search, current.search) ||
            conflict(base.favorites, draft.favorites, current.favorites)) return null
        return current.copy(
            gestures = if (draft.gestures != base.gestures) draft.gestures else current.gestures,
            homeScreen = if (draft.homeScreen != base.homeScreen) draft.homeScreen else current.homeScreen,
            search = if (draft.search != base.search) draft.search else current.search,
            favorites = if (draft.favorites != base.favorites) draft.favorites else current.favorites,
        )
    }
}

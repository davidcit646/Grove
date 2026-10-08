package tech.granet.grove

/** Setup edits only its lanes. A concurrent edit to the same lane requires a new review. */
internal object SetupMergePolicy {
    fun merge(base: Config, draft: Config, current: Config): Config? {
        val result = CoreBridge.portable("setup", org.json.JSONObject().put("base", org.json.JSONObject(base.json()))
            .put("draft", org.json.JSONObject(draft.json())).put("current", org.json.JSONObject(current.json())))
        if (result != null && !result.has("error")) {
            if (result.isNull("value")) return null
            return ConfigStore.parse(result.getJSONObject("value").toString())
        }
        fun <T> conflict(old: T, proposed: T, now: T) = proposed != old && now != old && now != proposed
        if (conflict(base.gestures, draft.gestures, current.gestures) ||
            conflict(base.homeScreen, draft.homeScreen, current.homeScreen) ||
            conflict(base.favorites, draft.favorites, current.favorites)) return null
        fun field(old: Boolean, proposed: Boolean, now: Boolean): Boolean? =
            if (conflict(old, proposed, now)) null else if (proposed != old) proposed else now
        val b = base.search; val d = draft.search; val c = current.search
        val search = SearchSettings(
            contacts = field(b.contacts, d.contacts, c.contacts) ?: return null,
            files = field(b.files, d.files, c.files) ?: return null,
            contactIndexing = field(b.contactIndexing, d.contactIndexing, c.contactIndexing) ?: return null,
            fileIndexing = field(b.fileIndexing, d.fileIndexing, c.fileIndexing) ?: return null,
            calculator = field(b.calculator, d.calculator, c.calculator) ?: return null,
            androidSettings = field(b.androidSettings, d.androidSettings, c.androidSettings) ?: return null,
            groveSettings = field(b.groveSettings, d.groveSettings, c.groveSettings) ?: return null,
        )
        return current.copy(
            gestures = if (draft.gestures != base.gestures) draft.gestures else current.gestures,
            homeScreen = if (draft.homeScreen != base.homeScreen) draft.homeScreen else current.homeScreen,
            search = search,
            favorites = if (draft.favorites != base.favorites) draft.favorites else current.favorites,
        )
    }
}

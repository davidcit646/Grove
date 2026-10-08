package tech.granet.grove

/** A saved indexing preference never authorizes a disabled search source. */
internal object IndexAccessPolicy {
    fun contacts(settings: SearchSettings, permitted: Boolean): Boolean =
        eligible(settings.contacts, settings.contactIndexing, permitted)
    fun files(settings: SearchSettings, permitted: Boolean): Boolean =
        eligible(settings.files, settings.fileIndexing, permitted)
    private fun eligible(search: Boolean, index: Boolean, permitted: Boolean): Boolean =
        PortablePolicy.bool("access", org.json.JSONObject().put("search", search).put("index", index).put("permitted", permitted))
            ?: (search && index && permitted)
}



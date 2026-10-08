package tech.granet.grove

internal object SearchPublicationGate {
    fun allowed(generation: Int, currentGeneration: Int, active: Boolean,
                enabled: Boolean, access: Boolean, cacheSupersedesLive: Boolean = false): Boolean =
        PortablePolicy.bool("publication", org.json.JSONObject().put("generation", generation).put("current", currentGeneration)
            .put("active", active).put("enabled", enabled).put("access", access).put("superseded", cacheSupersedesLive))
            ?: (generation == currentGeneration && active && enabled && access && !cacheSupersedesLive)
}


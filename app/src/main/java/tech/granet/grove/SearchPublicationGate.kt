package tech.granet.grove

/** A result may publish only while the originating query and protected source are still authoritative. */
internal object SearchPublicationGate {
    fun allowed(
        generation: Int,
        currentGeneration: Int,
        active: Boolean,
        enabled: Boolean,
        access: Boolean,
        cacheSupersedesLive: Boolean = false,
    ): Boolean = generation == currentGeneration && active && enabled && access && !cacheSupersedesLive
}

package tech.granet.grove

/** Fail closed: persistence must succeed before the active configuration is published. */
internal object ConfigTransaction {
    fun <T> commit(next: T, persist: (T) -> Unit, publish: (T) -> Unit,
                   unavailable: (Exception) -> Unit): Boolean {
        try {
            persist(next)
        } catch (error: Exception) {
            unavailable(error)
            return false
        }
        publish(next)
        return true
    }
}

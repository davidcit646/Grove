package tech.granet.grove

/** Revocation and cache commits share one lock. Failed persistence stays fail-closed. */
internal class IndexRevocationGate(val lock: Any) {
    private val blocked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun allowed(kind: String): Boolean = kind !in blocked
    fun renew(kind: String) = synchronized(lock) { blocked.remove(kind); Unit }
    fun cancel(kind: String, persist: () -> Boolean, cleanup: () -> Boolean): Boolean {
        blocked.add(kind)
        return synchronized(lock) { persist() && cleanup() }
    }
}

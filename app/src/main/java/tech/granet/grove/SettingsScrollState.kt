package tech.granet.grove

/** Transient UI position, keyed by route; never part of launcher configuration. */
internal class SettingsScrollState {
    private val positions = mutableMapOf<String, Int>()
    fun remember(route: String, y: Int) { positions[route] = y.coerceAtLeast(0) }
    fun position(route: String): Int = positions[route] ?: 0
    fun snapshot(): Map<String, Int> = positions.toMap()
    fun restore(saved: Map<String, Int>, routes: Set<String>) {
        positions.clear()
        saved.filterKeys { it in routes }.forEach { (route, y) -> remember(route, y) }
    }
}

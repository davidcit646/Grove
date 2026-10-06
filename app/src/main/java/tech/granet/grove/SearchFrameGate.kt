package tech.granet.grove

/** Identical source notifications must not rebuild rows or disturb focus/scroll. */
internal class SearchFrameGate {
    private var target: Any? = null
    private var fingerprint: Any? = null
    fun shouldRender(nextTarget: Any, next: Any): Boolean {
        if (target === nextTarget && fingerprint == next) return false
        target = nextTarget
        fingerprint = next
        return true
    }
}

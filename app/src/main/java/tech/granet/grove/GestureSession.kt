package tech.granet.grove

/** Android-free state transitions for a single Home or drawer touch sequence. */
internal class GestureSession {
    private var startX = 0f
    private var startY = 0f
    private var startedAt = 0L
    private var home = false
    private var drawer = false
    private var drawerAtTop = false
    private var blocked = false
    private var interactive = false
    private var moved = false
    private var captured = false
    private var drawerCaptured = false
    private var longPressed = false

    data class Move(
        val moved: Boolean = false,
        val cancelChildren: Boolean = false,
        val offset: Float? = null,
        val consume: Boolean = false,
    )
    data class Release(
        val consume: Boolean = false,
        val cancelChildren: Boolean = false,
        val settle: Boolean = false,
        val closeDrawer: Boolean = false,
        val gesture: HomeGesture = HomeGesture.NONE,
        val contextMenu: Boolean = false,
    )

    fun begin(x: Float, y: Float, time: Long, inDrawer: Boolean, atTop: Boolean,
              hasHome: Boolean, widget: Boolean, interactiveTarget: Boolean): Boolean {
        startX = x; startY = y; startedAt = time
        home = !inDrawer && hasHome
        drawerAtTop = inDrawer && atTop
        drawer = drawerAtTop
        blocked = home && widget
        interactive = home && interactiveTarget
        moved = false; captured = false; drawerCaptured = false; longPressed = false
        return home && !interactive && !blocked
    }

    val downTime: Long get() = startedAt
    fun verticalDelta(y: Float): Float = y - startY

    fun longPress(): Boolean {
        if (!home || interactive || blocked) return false
        longPressed = true
        home = false
        return true
    }

    fun cancel(): Boolean {
        val consume = captured || drawerCaptured || longPressed
        home = false; drawer = false; captured = false; drawerCaptured = false; longPressed = false
        return consume
    }

    fun move(x: Float, y: Float, time: Long, slop: Float, minimum: Float,
             settings: GestureSettings, scrollCanMove: Boolean, previewLimit: Float): Move {
        val dx = x - startX
        val dy = y - startY
        if (drawer) {
            val newlyCaptured = !drawerCaptured && dy > slop &&
                kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.1f
            if (newlyCaptured) drawerCaptured = true
            if (drawerCaptured) return Move(cancelChildren = newlyCaptured,
                offset = dy.coerceAtLeast(0f) * 0.72f, consume = true)
        }
        if (longPressed) return Move(consume = true)
        if (!home) return Move()
        val crossedSlop = dx * dx + dy * dy > slop * slop
        if (crossedSlop) {
            moved = true
            if (!captured && scrollCanMove) blocked = true
        }
        val newlyCaptured = !blocked && !captured &&
            Gestures.resolve(dx, dy, time - startedAt, minimum, settings) != HomeGesture.NONE
        if (newlyCaptured) captured = true
        return Move(moved = crossedSlop, cancelChildren = newlyCaptured,
            offset = if (!blocked && !interactive) (dy * 0.10f).coerceIn(-previewLimit, previewLimit) else null,
            consume = captured)
    }

    fun release(x: Float, y: Float, time: Long, slop: Float,
                homeMinimum: Float, drawerMinimum: Float, settings: GestureSettings,
                tapContext: Boolean): Release {
        val dx = x - startX
        val dy = y - startY
        if (drawer || drawerCaptured) {
            val close = drawerCaptured && Gestures.drawerClose(
                dx, dy, time - startedAt, drawerMinimum, drawerAtTop)
            val swallow = drawerAtTop && dy > slop
            drawer = false; drawerCaptured = false
            if (close) return Release(consume = true, closeDrawer = true)
            if (swallow) return Release(consume = true, settle = true)
            return Release(settle = true)
        }
        if (longPressed) {
            longPressed = false; home = false
            return Release(consume = true, settle = true)
        }
        if (!home) return Release()
        home = false
        val gesture = if (!blocked) Gestures.resolve(dx, dy, time - startedAt, homeMinimum, settings)
            else HomeGesture.NONE
        if (gesture != HomeGesture.NONE) {
            val cancel = !captured
            captured = false
            return Release(consume = true, cancelChildren = cancel, gesture = gesture)
        }
        val wasCaptured = captured
        captured = false
        val tap = !moved && dx * dx + dy * dy <= slop * slop && time - startedAt <= 350L
        return Release(consume = wasCaptured || (!interactive && tap && tapContext),
            cancelChildren = !interactive && tap && tapContext,
            settle = true, contextMenu = !interactive && tap && tapContext)
    }
}

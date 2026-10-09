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

    private fun native(action: String, vararg fields: Pair<String, Any?>): org.json.JSONObject? {
        val state = org.json.JSONObject().put("startX", startX.toDouble()).put("startY", startY.toDouble()).put("startedAt", startedAt)
            .put("home", home).put("drawer", drawer).put("drawerAtTop", drawerAtTop).put("blocked", blocked)
            .put("interactive", interactive).put("moved", moved).put("captured", captured).put("drawerCaptured", drawerCaptured).put("longPressed", longPressed)
        val args = org.json.JSONObject().put("action", action).put("state", state)
        fields.forEach { (key, value) -> args.put(key, value ?: org.json.JSONObject.NULL) }
        val output = PortablePolicy.value("gestureSession", args) as? org.json.JSONObject ?: return null
        return try {
            val next = output.getJSONObject("state")
            val x = next.getDouble("startX").toFloat(); val y = next.getDouble("startY").toFloat()
            require(x.isFinite() && y.isFinite())
            val time = next.getLong("startedAt")
            val flags = listOf("home", "drawer", "drawerAtTop", "blocked", "interactive", "moved", "captured", "drawerCaptured", "longPressed").map(next::getBoolean)
            startX = x; startY = y; startedAt = time
            home = flags[0]; drawer = flags[1]; drawerAtTop = flags[2]; blocked = flags[3]; interactive = flags[4]
            moved = flags[5]; captured = flags[6]; drawerCaptured = flags[7]; longPressed = flags[8]
            output.getJSONObject("result")
        } catch (_: Exception) { null }
    }

    fun begin(x: Float, y: Float, time: Long, inDrawer: Boolean, atTop: Boolean,
              hasHome: Boolean, widget: Boolean, interactiveTarget: Boolean): Boolean {
        if (x.isFinite() && y.isFinite()) native("begin", "x" to x.toDouble(), "y" to y.toDouble(), "time" to time,
            "inDrawer" to inDrawer, "atTop" to atTop, "hasHome" to hasHome, "widget" to widget,
            "interactiveTarget" to interactiveTarget)?.let { return it.getBoolean("value") }
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
        native("longPress")?.let { return it.getBoolean("value") }
        if (!home || interactive || blocked) return false
        longPressed = true
        home = false
        return true
    }

    fun cancel(): Boolean {
        native("cancel")?.let { return it.getBoolean("value") }
        val consume = captured || drawerCaptured || longPressed
        home = false; drawer = false; captured = false; drawerCaptured = false; longPressed = false
        return consume
    }

    fun move(x: Float, y: Float, time: Long, slop: Float, minimum: Float,
             settings: GestureSettings, scrollCanMove: Boolean, previewLimit: Float): Move {
        if (listOf(x,y,slop,minimum,previewLimit).all(Float::isFinite)) native("move",
            "x" to x.toDouble(), "y" to y.toDouble(), "time" to time, "slop" to slop.toDouble(),
            "minimum" to minimum.toDouble(), "down" to settings.swipeDownSearch, "up" to settings.swipeUpAppDrawer,
            "scroll" to scrollCanMove, "preview" to previewLimit.toDouble())?.let {
                return Move(it.getBoolean("moved"), it.getBoolean("cancelChildren"),
                    if (it.isNull("offset")) null else it.getDouble("offset").toFloat(), it.getBoolean("consume"))
            }
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
        if (listOf(x,y,slop,homeMinimum,drawerMinimum).all(Float::isFinite)) native("release",
            "x" to x.toDouble(), "y" to y.toDouble(), "time" to time, "slop" to slop.toDouble(),
            "minimum" to homeMinimum.toDouble(), "drawerMinimum" to drawerMinimum.toDouble(),
            "down" to settings.swipeDownSearch, "up" to settings.swipeUpAppDrawer, "tap" to tapContext)?.let {
                val gesture = it.getInt("gesture")
                if (gesture in HomeGesture.entries.indices) return Release(it.getBoolean("consume"), it.getBoolean("cancelChildren"),
                    it.getBoolean("settle"), it.getBoolean("closeDrawer"), HomeGesture.entries[gesture], it.getBoolean("contextMenu"))
            }
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

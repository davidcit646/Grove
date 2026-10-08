package tech.granet.grove

import kotlin.math.abs

enum class HomeGesture { NONE, SEARCH, APP_DRAWER }

/** Pure gesture classifiers so touch behavior can be tested without Android instrumentation. */
object Gestures {
    fun resolve(
        dx: Float,
        dy: Float,
        durationMs: Long,
        minimumDistance: Float,
        settings: GestureSettings,
    ): HomeGesture {
        if (dx.isFinite() && dy.isFinite() && minimumDistance.isFinite()) {
            PortablePolicy.int("gesture", org.json.JSONObject().put("dx", dx.toDouble()).put("dy", dy.toDouble())
                .put("minimum", minimumDistance.toDouble()).put("duration", durationMs)
                .put("down", settings.swipeDownSearch).put("up", settings.swipeUpAppDrawer), 0..2)
                ?.let { return HomeGesture.entries[it] }
        }
        if (durationMs !in 0..700) return HomeGesture.NONE
        if (abs(dy) < minimumDistance) return HomeGesture.NONE
        if (abs(dy) <= abs(dx) * 1.2f) return HomeGesture.NONE

        return when {
            dy > 0 && settings.swipeDownSearch -> HomeGesture.SEARCH
            dy < 0 && settings.swipeUpAppDrawer -> HomeGesture.APP_DRAWER
            else -> HomeGesture.NONE
        }
    }

    /** The drawer only closes on a deliberate downward swipe that began while it was at the top. */
    fun drawerClose(
        dx: Float,
        dy: Float,
        durationMs: Long,
        minimumDistance: Float,
        startedAtTop: Boolean,
    ): Boolean {
        if (!startedAtTop || durationMs !in 0..700) return false
        if (dy < minimumDistance) return false
        return abs(dy) > abs(dx) * 1.2f
    }
}

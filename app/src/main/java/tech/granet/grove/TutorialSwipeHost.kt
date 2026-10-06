package tech.granet.grove

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import tech.granet.grove.ui.dp

/** Horizontal page gestures coexist with vertical scrolling and accessible buttons. */
internal class TutorialSwipeHost(context: Context, private val busy: () -> Boolean,
                                 private val navigate: (Boolean) -> Unit) : FrameLayout(context) {
    private var x = 0f; private var y = 0f; private var time = 0L
    private var eligible = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                x = event.x; y = event.y; time = event.eventTime; eligible = !busy()
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> eligible = false
            MotionEvent.ACTION_MOVE -> if (eligible && !busy() && abs(event.x - x) > slop &&
                abs(event.x - x) > abs(event.y - y) * 1.2f) return true
        }
        return super.onInterceptTouchEvent(event)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - x; val dy = event.y - y
                if (eligible && !busy() && event.eventTime - time in 0..700 &&
                    abs(dx) >= context.dp(55) && abs(dx) > abs(dy) * 1.2f) {
                    navigate((dx < 0) != (layoutDirection == LAYOUT_DIRECTION_RTL))
                }
                eligible = false
                performClick()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> eligible = false
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()
}

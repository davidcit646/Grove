package tech.granet.grove

import android.graphics.Rect
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.dp

/** Owns pinned-tile hold, drag, and drop state across Home views. */
internal class PinDragController(
    private val activity: AppCompatActivity,
    private val scroll: () -> ScrollView?,
    private val config: () -> Config,
    private val commit: (Config) -> Boolean,
    private val appMenu: (App) -> Unit,
    private val cancelHomeTouch: () -> Unit,
) {
    data class Drag(val key: String)
    private var active: Drag? = null
    private var held: View? = null
    val busy: Boolean get() = active != null || held != null

    fun scrollNearEdge(target: View, event: DragEvent) {
        val view = scroll() ?: return
        val bounds = Rect(); view.getGlobalVisibleRect(bounds)
        val location = IntArray(2); target.getLocationOnScreen(location)
        val y = event.y + location[1]
        if (y < bounds.top + activity.dp(64)) view.smoothScrollBy(0, -activity.dp(28))
        else if (y > bounds.bottom - activity.dp(64)) view.smoothScrollBy(0, activity.dp(28))
    }

    fun releaseHold() {
        held?.apply {
            scaleX = 1f; scaleY = 1f
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        held = null
    }

    fun finishDrag() {
        active = null
        releaseHold()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, app: App) {
        var downX = 0f
        var downY = 0f
        var touchActive = false
        var touchCanceled = false
        view.setOnLongClickListener {
            // Accessibility long-click has no pointer to drag: open app options directly.
            if (!touchActive) appMenu(app) else {
                held = view
                cancelHomeTouch()
                view.parent.requestDisallowInterceptTouchEvent(true)
                view.scaleX = 1.08f; view.scaleY = 1.08f
            }
            true
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; touchActive = true; touchCanceled = false
                }
                MotionEvent.ACTION_MOVE -> if (held === view) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    val slop = ViewConfiguration.get(activity).scaledTouchSlop
                    if (dx * dx + dy * dy > slop * slop) {
                        val drag = Drag(app.key)
                        active = drag
                        val started = view.startDragAndDrop(null, View.DragShadowBuilder(view), drag, 0)
                        releaseHold()
                        if (!started) active = null
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    if (touchCanceled) { view.isPressed = false; return@setOnTouchListener true }
                    if (held === view) {
                        releaseHold(); view.isPressed = false; appMenu(app)
                        return@setOnTouchListener true
                    }
                    if (active != null) return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchActive = false; touchCanceled = true
                    view.cancelLongPress(); view.isPressed = false
                    if (held === view) releaseHold()
                }
            }
            false
        }
        view.setOnDragListener { target, event ->
            val drag = event.localState as? Drag
            if (drag == null) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_LOCATION -> scrollNearEdge(target, event)
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        target.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100L).start()
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        target.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    }
                    DragEvent.ACTION_DROP -> {
                        DrawerState.movePin(config(), drag.key, app.key)?.let { commit(it) }
                    }
                }
                true
            }
        }
    }
}

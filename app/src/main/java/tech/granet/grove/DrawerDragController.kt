package tech.granet.grove

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Owns a drawer tile's hold, menu, and Android drag initiation. */
internal class DrawerDragController(private val showMenu: (App) -> Unit) {
    data class Drag(val key: String)

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, app: App) {
        var downX = 0f
        var downY = 0f
        var touchActive = false
        var dragArmed = false
        var dragging = false
        fun release() {
            dragArmed = false
            view.scaleX = 1f; view.scaleY = 1f
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        view.setOnLongClickListener {
            // A stationary hold opens actions on release. Moving after the
            // hold starts a drag, so either gesture can be used on one app.
            if (!touchActive) showMenu(app) else {
                dragArmed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                view.scaleX = 1.08f; view.scaleY = 1.08f
            }
            true
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    touchActive = true; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (dragArmed) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                    if (dx * dx + dy * dy > slop * slop) {
                        dragging = view.startDragAndDrop(null, View.DragShadowBuilder(view), Drag(app.key), 0)
                        release()
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    if (dragArmed) {
                        release(); view.isPressed = false; showMenu(app)
                        return@setOnTouchListener true
                    }
                    if (dragging) { dragging = false; return@setOnTouchListener true }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchActive = false; dragging = false
                    view.cancelLongPress(); view.isPressed = false
                    if (dragArmed) release()
                }
            }
            false
        }
    }
}

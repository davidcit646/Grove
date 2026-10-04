package tech.granet.grove

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import tech.granet.grove.ui.dp

/** Routes Android touches through the tested GestureSession before changing Home views. */
internal class HomeTouchRouter(
    private val context: Context,
    private val root: () -> LinearLayout,
    private val drawerGrid: () -> GridView?,
    private val inDrawer: () -> Boolean,
    private val settings: () -> GestureSettings,
    private val contextMenu: () -> Unit,
    private val gesture: (HomeGesture) -> Unit,
    private val closeDrawer: () -> Unit,
) {
    private val session = GestureSession()
    private val handler = Handler(Looper.getMainLooper())
    private var touchedScroll: ScrollView? = null
    private val longPress = Runnable {
        if (settings().longPressHomeContextMenu && session.longPress()) {
            dispatchChild?.let(::cancelChildTouch)
            contextMenu()
        }
    }
    private var dispatchChild: ((MotionEvent) -> Boolean)? = null

    fun cancel() {
        handler.removeCallbacks(longPress)
        session.cancel()
        touchedScroll = null
    }

    fun dispatch(event: MotionEvent, child: (MotionEvent) -> Boolean): Boolean {
        dispatchChild = child
        val home = root()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                home.animate().cancel()
                home.translationY = 0f
                home.alpha = 1f
                handler.removeCallbacks(longPress)
                val drawer = inDrawer()
                val grid = drawerGrid()
                val atTop = drawer && grid != null && !grid.canScrollVertically(-1) &&
                    pointInside(grid, event.rawX, event.rawY)
                val widget = !drawer && insideWidget(home, event.rawX, event.rawY)
                val interactive = !drawer && insideInteractive(home, home, event.rawX, event.rawY)
                touchedScroll = if (!drawer) scrollAt(home, event.rawX, event.rawY) else null
                val schedule = session.begin(event.rawX, event.rawY, event.eventTime,
                    drawer, atTop, true, widget, interactive)
                if (schedule && settings().longPressHomeContextMenu)
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                val consume = session.cancel()
                settle(home)
                if (consume) return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = session.verticalDelta(event.rawY)
                val scrollCanMove = touchedScroll?.canScrollVertically(if (dy > 0) -1 else 1) == true
                val step = session.move(event.rawX, event.rawY, event.eventTime,
                    ViewConfiguration.get(context).scaledTouchSlop.toFloat(), context.dp(72).toFloat(),
                    settings(), scrollCanMove, context.dp(24).toFloat())
                if (step.moved) handler.removeCallbacks(longPress)
                if (step.cancelChildren) cancelChildTouch(child)
                step.offset?.let { home.translationY = it }
                if (step.consume) return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                val current = settings()
                val step = session.release(event.rawX, event.rawY, event.eventTime,
                    ViewConfiguration.get(context).scaledTouchSlop.toFloat(), context.dp(72).toFloat(),
                    context.dp(88).toFloat(), current, current.tapHomeContextMenu)
                if (step.cancelChildren) cancelChildTouch(child)
                if (step.closeDrawer) closeDrawer()
                else if (step.gesture != HomeGesture.NONE) gesture(step.gesture)
                else if (step.contextMenu) contextMenu()
                else if (step.settle) settle(home)
                if (step.consume) return true
            }
        }
        return child(event)
    }

    private fun cancelChildTouch(child: (MotionEvent) -> Boolean) {
        val cancel = MotionEvent.obtain(session.downTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        child(cancel)
        cancel.recycle()
    }

    private fun settle(home: View) {
        home.animate().cancel()
        home.animate().translationY(0f).setDuration(140L).start()
    }

    private fun pointInside(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x < location[0] + view.width &&
            y >= location[1] && y < location[1] + view.height
    }

    private fun scrollAt(view: View, x: Float, y: Float): ScrollView? {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x.toInt(), y.toInt())) return null
        if (view is ScrollView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            scrollAt(view.getChildAt(index), x, y)?.let { return it }
        }
        return null
    }

    private fun insideWidget(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view is AppWidgetHostView) return pointInside(view, x, y)
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            if (insideWidget(view.getChildAt(index), x, y)) return true
        }
        return false
    }

    private fun insideInteractive(view: View, home: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view !== home &&
            (view.isClickable || view.isLongClickable || view is EditText || view is AppWidgetHostView) &&
            pointInside(view, x, y)) return true
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            if (insideInteractive(view.getChildAt(index), home, x, y)) return true
        }
        return false
    }
}

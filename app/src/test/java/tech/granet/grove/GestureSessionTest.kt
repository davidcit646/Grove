package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureSessionTest {
    private val settings = GestureSettings()

    @Test fun homeSwipeAndDisabledGesture() {
        val enabled = GestureSession()
        enabled.begin(20f, 200f, 100L, false, false, true, false, false)
        val moved = enabled.move(20f, 90f, 200L, 8f, 72f, settings, false, 24f)
        assertTrue(moved.cancelChildren)
        assertTrue(moved.consume)
        assertEquals(HomeGesture.APP_DRAWER,
            enabled.release(20f, 90f, 220L, 8f, 72f, 88f, settings, false).gesture)

        val disabled = GestureSession()
        disabled.begin(20f, 200f, 100L, false, false, true, false, false)
        val off = settings.copy(swipeUpAppDrawer = false)
        assertFalse(disabled.move(20f, 90f, 200L, 8f, 72f, off, false, 24f).consume)
        assertEquals(HomeGesture.NONE,
            disabled.release(20f, 90f, 220L, 8f, 72f, 88f, off, false).gesture)
    }

    @Test fun scrollableWidgetAndInteractiveTargetKeepTouch() {
        val widget = GestureSession()
        widget.begin(0f, 150f, 0L, false, false, true, true, true)
        assertFalse(widget.move(0f, 0f, 100L, 8f, 72f, settings, true, 24f).consume)
        assertEquals(HomeGesture.NONE,
            widget.release(0f, 0f, 120L, 8f, 72f, 88f, settings, true).gesture)

        val scroll = GestureSession()
        scroll.begin(0f, 150f, 0L, false, false, true, false, false)
        assertFalse(scroll.move(0f, 0f, 100L, 8f, 72f, settings, true, 24f).consume)
    }

    @Test fun drawerOnlyClosesWhenStartedAtTopAndInterruptedCaptureClears() {
        val drawer = GestureSession()
        drawer.begin(0f, 0f, 0L, true, true, false, false, false)
        assertTrue(drawer.move(0f, 120f, 100L, 8f, 72f, settings, false, 24f).cancelChildren)
        assertTrue(drawer.release(0f, 120f, 150L, 8f, 72f, 88f, settings, false).closeDrawer)

        val below = GestureSession()
        below.begin(0f, 0f, 0L, true, false, false, false, false)
        assertFalse(below.move(0f, 120f, 100L, 8f, 72f, settings, false, 24f).consume)
        assertFalse(below.release(0f, 120f, 150L, 8f, 72f, 88f, settings, false).closeDrawer)

        val interrupted = GestureSession()
        interrupted.begin(0f, 0f, 0L, true, true, false, false, false)
        interrupted.move(0f, 120f, 100L, 8f, 72f, settings, false, 24f)
        assertTrue(interrupted.cancel())
        assertFalse(interrupted.cancel())
    }

    @Test fun longPressAndTapAreExclusive() {
        val long = GestureSession()
        assertTrue(long.begin(0f, 0f, 0L, false, false, true, false, false))
        assertTrue(long.longPress())
        assertFalse(long.release(0f, 0f, 600L, 8f, 72f, 88f, settings, true).contextMenu)

        val tap = GestureSession()
        tap.begin(0f, 0f, 0L, false, false, true, false, false)
        assertTrue(tap.release(0f, 0f, 100L, 8f, 72f, 88f, settings, true).contextMenu)
    }
}

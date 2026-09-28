package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturesTest {
    @Test fun swipeDownOpensSearchWhenEnabled() {
        assertEquals(HomeGesture.SEARCH, Gestures.resolve(4f, 120f, 250, 60f, GestureSettings()))
    }

    @Test fun swipeUpOpensDrawerWhenEnabled() {
        assertEquals(HomeGesture.APP_DRAWER, Gestures.resolve(3f, -120f, 250, 60f, GestureSettings()))
    }

    @Test fun disabledGesturesDoNothing() {
        val off = GestureSettings(swipeDownSearch = false, swipeUpAppDrawer = false)
        assertEquals(HomeGesture.NONE, Gestures.resolve(0f, 120f, 250, 60f, off))
        assertEquals(HomeGesture.NONE, Gestures.resolve(0f, -120f, 250, 60f, off))
    }

    @Test fun horizontalMovementIsIgnored() {
        assertEquals(HomeGesture.NONE, Gestures.resolve(120f, 70f, 250, 60f, GestureSettings()))
    }

    @Test fun tinyAndSlowMovementsAreIgnored() {
        assertEquals(HomeGesture.NONE, Gestures.resolve(0f, 30f, 250, 60f, GestureSettings()))
        assertEquals(HomeGesture.NONE, Gestures.resolve(0f, 120f, 900, 60f, GestureSettings()))
    }

    @Test fun drawerClosesOnlyFromTop() {
        assertTrue(Gestures.drawerClose(2f, 120f, 240, 70f, startedAtTop = true))
        assertFalse(Gestures.drawerClose(2f, 120f, 240, 70f, startedAtTop = false))
    }

    @Test fun drawerCloseRejectsHorizontalTinyAndSlowGestures() {
        assertFalse(Gestures.drawerClose(130f, 90f, 240, 70f, startedAtTop = true))
        assertFalse(Gestures.drawerClose(0f, 40f, 240, 70f, startedAtTop = true))
        assertFalse(Gestures.drawerClose(0f, 120f, 900, 70f, startedAtTop = true))
    }
}

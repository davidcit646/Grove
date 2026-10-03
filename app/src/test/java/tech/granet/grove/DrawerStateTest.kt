package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerStateTest {
    @Test fun selectionClearsWhenModeEnds() {
        val state = DrawerState()
        state.toggleMode()
        state.toggle("a")
        state.select("b")
        assertEquals(setOf("a", "b"), state.keys)
        state.toggle("a")
        assertEquals(setOf("b"), state.keys)
        state.toggleMode()
        assertFalse(state.selecting)
        assertTrue(state.keys.isEmpty())
    }

    @Test fun invalidFolderEditsLeaveOriginalConfigUntouched() {
        val original = Config(folders = listOf(AppFolder("Work", listOf("a"))))
        assertNull(DrawerState.createFolder(original, " work ", listOf("b")))
        assertNull(DrawerState.renameFolder(original, "missing", "New"))
        assertNull(DrawerState.moveToFolder(original, setOf("b"), "missing"))
        assertEquals(listOf("a"), original.folders.single().apps)
    }

    @Test fun folderMoveIsAtomicAndPinMoveRequiresBothMembers() {
        val original = Config(
            folders = listOf(AppFolder("Work", listOf("a")), AppFolder("Play", listOf("b"))),
            favorites = listOf("a", "b"),
        )
        val moved = DrawerState.moveToFolder(original, setOf("a"), "Play")!!
        assertTrue(moved.folders.first().apps.isEmpty())
        assertEquals(listOf("b", "a"), moved.folders.last().apps)
        assertEquals(listOf("a"), original.folders.first().apps)
        assertNull(DrawerState.movePin(original, "missing", "b"))
        assertEquals(listOf("b", "a"), DrawerState.movePin(original, "a", "b")!!.favorites)
    }
}

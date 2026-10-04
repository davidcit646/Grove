package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UninstallBatchTest {
    @Test fun acceptedItemsContinueInOrderWithoutDuplicatePackages() {
        val batch = UninstallBatch()
        assertEquals("one", batch.start(listOf("one", "one", "two")))
        assertEquals("two", batch.accepted())
        assertNull(batch.accepted())
    }

    @Test fun cancelStopsEverythingAfterTheCurrentDialog() {
        val batch = UninstallBatch()
        assertEquals("one", batch.start(listOf("one", "two", "three")))
        assertEquals(2, batch.cancel())
        assertNull(batch.accepted())
        assertEquals("fresh", batch.start(listOf("fresh")))
    }

    @Test fun emptyBatchCannotAdvance() {
        val batch = UninstallBatch()
        assertNull(batch.start(listOf("", "  ".trim())))
        assertNull(batch.accepted())
    }
}

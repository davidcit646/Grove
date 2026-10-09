package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BoundaryFailureTest {
    @Test fun failedRevocationBlocksWritesAndSkipsCleanup() {
        val gate = IndexRevocationGate(Any())
        var cleaned = false
        assertFalse(gate.cancel("files", { false }, { cleaned = true; true }))
        assertFalse(gate.allowed("files"))
        assertFalse(cleaned)
        assertTrue(gate.cancel("files", { true }, { true }))
        gate.renew("files")
        assertTrue(gate.allowed("files"))
    }
    @Test fun cancellationWaitsForCommitAndLeavesNoPublishedSnapshot() {
        val gate = IndexRevocationGate(Any())
        val writing = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var snapshot = false
        val writer = Thread { synchronized(gate.lock) {
            writing.countDown()
            check(finish.await(2, TimeUnit.SECONDS))
            if (gate.allowed("files")) snapshot = true
        } }
        writer.start()
        assertTrue(writing.await(2, TimeUnit.SECONDS))
        val result = java.util.concurrent.FutureTask { gate.cancel("files", { true }, { snapshot = false; true }) }
        val cancel = Thread(result)
        cancel.start()
        finish.countDown()
        writer.join(2000); cancel.join(2000)
        assertTrue(result.get(2, TimeUnit.SECONDS))
        assertFalse(writer.isAlive); assertFalse(cancel.isAlive)
        assertFalse(snapshot); assertFalse(gate.allowed("files"))
    }
    @Test fun boundedReadRejectsOverflowAndMakesProgressOnZeroReads() {
        assertArrayEquals(byteArrayOf(1,2), BoundedInput.read(ByteArrayInputStream(byteArrayOf(1,2)), 2))
        try { BoundedInput.read(ByteArrayInputStream(byteArrayOf(1,2,3)), 2); fail() }
        catch (_: IllegalArgumentException) { }
        val zeroBulk = object : InputStream() {
            var next = 0
            override fun read(): Int = if (next++ == 0) 7 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int = 0
        }
        assertArrayEquals(byteArrayOf(7), BoundedInput.read(zeroBulk, 2))
    }
}

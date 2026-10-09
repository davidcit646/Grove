package tech.granet.grove

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteOrder

class SearchBufferTest {
    @Test fun transportIsReadOnlyAndRejectsLossyOrOversizedLabels() {
        val buffer = SearchBuffer.prepare(arrayOf("café", "😀", "東京"))!!
        assertTrue(buffer.isDirect)
        assertTrue(buffer.isReadOnly)
        val reader = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, reader.int)
        assertEquals(3, reader.int)
        for (label in listOf("café", "😀", "東京")) {
            val bytes = ByteArray(reader.int)
            reader.get(bytes)
            assertEquals(label, bytes.toString(Charsets.UTF_8))
        }
        assertFalse(reader.hasRemaining())
        assertNull(SearchBuffer.prepare(arrayOf("\uD800")))
        assertNull(SearchBuffer.prepare(arrayOf("\uDC00")))
        assertNull(SearchBuffer.prepare(arrayOf("x".repeat(4097))))
        assertNull(SearchBuffer.prepare(Array(50_001) { "x" }))
        assertNull(SearchBuffer.prepare(Array(4096) { "x".repeat(4096) }))
    }

    @Test fun preparedBridgeMatchesArrayBridgeIncludingUnicodeAndTies() {
        assumeTrue(System.getProperty("grove.native.tests") == "true")
        assertTrue(CoreBridge.nativeAvailable)
        val labels = Array(3000) { listOf("café", "😀 tool", "東京", "document", "documemt", "document")[it % 6] }
        val buffer = SearchBuffer.prepare(labels)!!
        for (text in listOf("café", "😀", "東京", "document", "documant", "zzz")) {
            val query = Search.prepare(text)
            for (limit in listOf(0, 1, 12, Int.MAX_VALUE)) {
                val native = CoreBridge.preparedOrder(labels.size, buffer, query, limit)
                assertNotNull("Packed transport must execute without fallback", native)
                assertArrayEquals(CoreBridge.searchOrder(labels, query, limit), native)
                assertArrayEquals(native, CoreBridge.searchOrder(labels, buffer, query, limit))
            }
        }
        // Corrupt framing must recover, never publish unvalidated native indices.
        val corrupt = java.nio.ByteBuffer.allocateDirect(8)
        corrupt.putLong(-1L)
        assertNull(CoreBridge.preparedOrder(labels.size, corrupt, Search.prepare("document"), 12))
        assertArrayEquals(CoreBridge.searchOrder(labels, Search.prepare("document"), 12),
            CoreBridge.searchOrder(labels, corrupt, Search.prepare("document"), 12))
    }

    @Test fun compareJniTransports() {
        assumeTrue(System.getProperty("grove.native.tests") == "true")
        assertTrue(CoreBridge.nativeAvailable)
        println("JNI_BENCH rows,query,array_p50_us,buffer_p50_us,array_p95_us,buffer_p95_us,buffer_bytes,prepare_us")
        for (count in listOf(15_000, 50_000)) {
            val labels = Array(count) { "document $it monthly summary report" }
            val start = System.nanoTime()
            val buffer = SearchBuffer.prepare(labels)!!
            val prepare = (System.nanoTime() - start) / 1000
            for (text in listOf("document", "documemt", "zzzzzz")) {
                val query = Search.prepare(text)
                val expected = CoreBridge.searchOrder(labels, query, 12)
                val before = LongArray(35)
                val after = LongArray(35)
                repeat(5) { CoreBridge.searchOrder(labels, query, 12); CoreBridge.searchOrder(labels, buffer, query, 12) }
                fun measure(packed: Boolean): Long {
                    val began = System.nanoTime()
                    val actual = if (packed) CoreBridge.searchOrder(labels, buffer, query, 12) else CoreBridge.searchOrder(labels, query, 12)
                    val elapsed = (System.nanoTime() - began) / 1000
                    assertArrayEquals(expected, actual)
                    return elapsed
                }
                repeat(35) { sample ->
                    if (sample % 2 == 0) { before[sample] = measure(false); after[sample] = measure(true) }
                    else { after[sample] = measure(true); before[sample] = measure(false) }
                }
                before.sort(); after.sort()
                println("JNI_BENCH $count,$text,${before[17]},${after[17]},${before[33]},${after[33]},${buffer.capacity()},$prepare")
            }
        }
    }
}

package tech.granet.grove

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeResultsTest {
    @Test fun searchRejectsDuplicatesAndOutOfRangeIndices() {
        assertArrayEquals(intArrayOf(2, 0), NativeResults.search(intArrayOf(2, 0), 3, 2))
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.search(intArrayOf(1, 1), 3, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.search(intArrayOf(3), 3, 2)
        }
    }

    @Test fun mimeRejectsMissingOrCorruptResults() {
        val expected = listOf("audio/mp4" to "Audio", null)
        assertEquals(expected, NativeResults.mime(arrayOf("audio/mp4|Audio", ""), expected))
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.mime(arrayOf("audio/mp4|Audio"), expected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.mime(arrayOf("wrong|", ""), expected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.mime(arrayOf("audio/mp4|Videos", ""), expected)
        }
    }

    @Test fun wallpaperRejectsTruncatedPixelsAndOverflowSize() {
        assertArrayEquals(intArrayOf(1, 2), NativeResults.wallpaper(intArrayOf(1, 2), 1, 2))
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.wallpaper(intArrayOf(1), 1, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeResults.wallpaper(intArrayOf(), Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }
}

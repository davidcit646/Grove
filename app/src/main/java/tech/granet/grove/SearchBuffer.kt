package tech.granet.grove

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Immutable, snapshot-owned JNI transport. No native handle or protected-source authority. */
internal object SearchBuffer {
    private const val MAX_BYTES = 16 * 1024 * 1024

    fun prepare(labels: Array<String>): ByteBuffer? {
        if (labels.size > 50_000 || labels.any { it.length > 4096 || !validUtf16(it) }) return null
        var size = 8L
        val encoded = ArrayList<ByteArray>(labels.size)
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            size += 4L + bytes.size
            if (size > MAX_BYTES) return null
            encoded.add(bytes)
        }
        val buffer = ByteBuffer.allocateDirect(size.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1).putInt(labels.size)
        encoded.forEach { buffer.putInt(it.size).put(it) }
        buffer.flip()
        return buffer.asReadOnlyBuffer()
    }

    private fun validUtf16(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val unit = text[index++]
            if (unit.isHighSurrogate()) {
                if (index == text.length || !text[index++].isLowSurrogate()) return false
            } else if (unit.isLowSurrogate()) return false
        }
        return true
    }
}

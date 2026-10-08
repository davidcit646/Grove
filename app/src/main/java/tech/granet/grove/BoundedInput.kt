package tech.granet.grove

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Exact byte bound, including streams that return zero or change size during reading. */
internal object BoundedInput {
    fun read(input: InputStream, limit: Int): ByteArray {
        require(limit in 0 until Int.MAX_VALUE)
        val output = ByteArrayOutputStream(minOf(limit, 4096))
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                output.write(byte)
            } else output.write(buffer, 0, count)
            require(output.size() <= limit) { "Input exceeds size limit" }
        }
        return output.toByteArray()
    }
}

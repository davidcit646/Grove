package tech.granet.grove

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Bounded document I/O; parsing completes before a caller may activate settings. */
internal object ConfigDocuments {
    private const val LIMIT = 65_536

    fun read(input: InputStream): Config {
        val bytes = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val count = input.read(chunk, 0, minOf(chunk.size, LIMIT + 1 - bytes.size()))
            if (count < 0) break
            if (count == 0) continue
            bytes.write(chunk, 0, count)
            require(bytes.size() <= LIMIT) { "Configuration exceeds 64 KB" }
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        return ConfigStore.parse(text)
    }

    fun write(config: Config, output: OutputStream) {
        val bytes = config.json().toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= LIMIT) { "Configuration exceeds 64 KB" }
        output.write(bytes)
        output.flush()
    }
}

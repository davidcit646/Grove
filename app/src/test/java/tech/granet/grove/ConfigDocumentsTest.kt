package tech.granet.grove

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigDocumentsTest {
    @Test fun oldVersionDocumentImportsAndExportsAsCurrent() {
        val old = """{"version":1,"wallpaper":0,"favorites":[]}"""
        val config = ConfigDocuments.read(ByteArrayInputStream(old.toByteArray()))
        val output = ByteArrayOutputStream()
        ConfigDocuments.write(config, output)
        assertEquals(config, ConfigDocuments.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun oversizedAndMalformedUtf8DocumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigDocuments.read(ByteArrayInputStream(ByteArray(65_537) { 'x'.code.toByte() }))
        }
        assertThrows(Exception::class.java) {
            ConfigDocuments.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
    }

    @Test fun partialReadAndWriteNeverReturnSuccess() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("provider disconnected")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("provider disconnected")
        }
        assertThrows(IOException::class.java) { ConfigDocuments.read(broken) }
        val output = object : OutputStream() {
            override fun write(b: Int) = throw IOException("storage full")
            override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("storage full")
        }
        assertThrows(IOException::class.java) { ConfigDocuments.write(Config(), output) }
    }
}

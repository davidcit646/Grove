package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class NativeEnvelopeTest {
    @Test fun malformedProtocolCannotVetoOrPopulateConfiguration() {
        for (text in listOf("{}", "{\"version\":\"1\",\"value\":{}}", "{\"version\":1,\"value\":null}",
            "{\"version\":1,\"value\":{},\"error\":\"invalid\"}", "{\"version\":1,\"error\":true}")) {
            assertThrows(Exception::class.java) { NativeEnvelope.decode("config", text) }
        }
        assertTrue(NativeEnvelope.decode("config", "{\"version\":1,\"error\":\"invalid\"}").has("error"))
        assertTrue(NativeEnvelope.decode("config", "{\"version\":1,\"value\":{}}").has("value"))
    }
    @Test fun wrongPrimitiveTypeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { NativeEnvelope.decode("access", "{\"version\":1,\"value\":1}") }
        assertThrows(IllegalArgumentException::class.java) { NativeEnvelope.decode("columns", "{\"version\":1,\"value\":4.2}") }
    }
}

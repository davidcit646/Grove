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
    @Test fun malformedCanonicalPayloadUsesRecoveryWhileSchemaErrorRemainsInvalid() {
        val config = Config(themeMode = ThemeMode.DARK)
        val malformed = org.json.JSONObject().put("version", 1).put("value", org.json.JSONObject())
        assertEquals(config, ConfigCodec.resolveNative(config.json(), malformed))
        val invalid = org.json.JSONObject().put("version", 1).put("error", "Invalid schema")
        assertThrows(IllegalArgumentException::class.java) { ConfigCodec.resolveNative(config.json(), invalid) }
    }

}

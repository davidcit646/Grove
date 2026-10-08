package tech.granet.grove

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class NativeParityTest {
    private fun native() {
        assumeTrue(System.getProperty("grove.native.tests") == "true")
        assertTrue("Native CI must load the host cdylib", CoreBridge.nativeAvailable)
    }
    @Test fun normalizationMatchesJvmCorpusInBatches() {
        native()
        val labels = listOf("CAFÉ", "ΑΘΗΝΑ Σ", "İstanbul", "résumé", "a\u0301", "😀 Tool", "\t Hello \n", "東京", "Straße")
        val batch = List(3000) { labels[it % labels.size] }
        assertEquals(batch.map(Search::normalizeFallback), CoreBridge.normalizeAll(batch))
    }
    @Test fun calculatorMatchesDecimalRecovery() {
        native()
        for (expression in listOf("0.1+0.2=", "1/3=", "-10/6=", "1/7=", "1/40=", "(1+2)*3=", "2×4−1=", "1/0=", "4..2=", "123=", "foo=", "=", "0.00000000000000001/3=", "100000000000000000000000000/3=")) {
            assertNotNull(CoreBridge.portable("calculator", org.json.JSONObject().put("text", expression)))
            assertEquals(expression, SearchCalculator.fallback(expression), SearchCalculator.calculate(expression))
        }
    }
    @Test fun historicalConfigMatchesRecoveryDecoder() {
        native()
        for (version in 1..12) {
            val root = org.json.JSONObject().put("version", version).put("wallpaper", if (version >= 9) "grove-fern" else 0)
                .put("favorites", org.json.JSONArray(listOf("a/b", "a/b")))
            if (version >= 10) root.put("themeMode", "system")
            val text = root.toString()
            assertEquals("version $version", ConfigCodec.recovery(text), ConfigCodec.parse(text))
        }
    }
}

package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeFailureReporterTest {
    @Test fun repeatedFailuresReportOncePerOperationAndClass() {
        val events = mutableListOf<String>()
        val reporter = NativeFailureReporter { operation, error ->
            events += "$operation:${error.javaClass.simpleName}"
        }
        reporter.failed("search", IllegalArgumentException("bad result"))
        reporter.failed("search", IllegalArgumentException("again"))
        reporter.failed("search", UnsatisfiedLinkError("missing symbol"))
        reporter.failed("mime", IllegalArgumentException("bad result"))
        assertEquals(listOf("search:IllegalArgumentException", "search:UnsatisfiedLinkError",
            "mime:IllegalArgumentException"), events)
    }

    @Test fun kotlinMimeOverridesSurviveMissingNativeLibrary() {
        assertEquals("audio/mp4" to "Audio", CoreBridge.extensionOverride("m4a"))
        assertEquals("text/csv" to "Documents", CoreBridge.extensionOverride("csv"))
        assertEquals("video/x-matroska" to "Videos", CoreBridge.extensionOverride("mkv"))
        assertEquals("audio/ogg" to "Audio", CoreBridge.extensionOverride("opus"))
        assertEquals("audio/webm" to "Audio", CoreBridge.extensionOverride("weba"))
        assertEquals(null, CoreBridge.extensionOverride("jpg"))
    }
}

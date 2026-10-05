package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class CrashReporterPrivacyTest {
    @Test fun diagnosticOmitsExceptionMessageAndPrivatePath() {
        val error = IllegalStateException("secret /storage/emulated/0/private.txt user@example.com")
        error.stackTrace = arrayOf(StackTraceElement("tech.granet.grove.Example", "run", "Example.kt", 42))
        val text = CrashReporter.safeDiagnostic(error)!!
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("tech.granet.grove.Example.run(Example.kt:42)"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("/storage/"))
        assertFalse(text.contains("user@example.com"))
    }
}

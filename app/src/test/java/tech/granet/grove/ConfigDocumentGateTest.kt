package tech.granet.grove

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ConfigDocumentGateTest {
    @Test fun slowImportCannotReplaceNewerUserChoices() {
        val started = SettingsSnapshot(Config(themeMode = ThemeMode.SYSTEM), 0)
        val changed = SettingsSnapshot(started.config.copy(themeMode = ThemeMode.DARK), 1)
        assertFalse(ConfigDocumentGate.canActivate(started, changed))
        assertFalse(ConfigDocumentGate.canActivate(started, SettingsSnapshot(started.config.copy(search = SearchSettings(files = true)), 1)))
        assertTrue(ConfigDocumentGate.canActivate(started, started.copy()))
    }
    @Test fun zeroLengthChunkReadDoesNotSpinOrLoseInput() {
        val original = Config(themeMode = ThemeMode.WALLPAPER)
        val bytes = ByteArrayInputStream(original.json().toByteArray())
        val input = object : InputStream() {
            override fun read(): Int = bytes.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertEquals(original, ConfigDocuments.read(input))
    }
    @Test fun boundedContactCoverageIsNeverLabeledComplete() {
        assertFalse(ContactCoverage.isPartial(49_999, 50_000))
        assertTrue(ContactCoverage.isPartial(50_000, 50_000))
        assertTrue(ContactCoverage.isPartial(50_001, 50_000))
    }
}

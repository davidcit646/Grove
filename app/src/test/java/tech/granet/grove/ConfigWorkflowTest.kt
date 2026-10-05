package tech.granet.grove

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ConfigWorkflowTest {
    @Test fun failedImportDoesNotMutateActiveSettings() {
        var active = Config(wallpaper = 2)
        val workflow = ConfigWorkflow({ active }) { next -> active = next; true }
        assertThrows(IllegalArgumentException::class.java) {
            workflow.import(ByteArrayInputStream("""{"version":99,"wallpaper":0,"favorites":[]}""".toByteArray()))
        }
        assertEquals(2, active.wallpaper)
    }

    @Test fun oldVersionImportActivatesOnlyAfterParse() {
        var active = Config(wallpaper = 2)
        val workflow = ConfigWorkflow({ active }) { next -> active = next; true }
        assertTrue(workflow.import(ByteArrayInputStream("""{"version":1,"wallpaper":0,"favorites":[]}""".toByteArray())))
        assertEquals(0, active.wallpaper)
        assertFalse(active.search.contactIndexing)
        assertFalse(active.search.fileIndexing)
    }

    @Test fun activationFailureLeavesCurrentConfigInPlace() {
        val active = Config(wallpaper = 2)
        val workflow = ConfigWorkflow({ active }) { false }
        assertFalse(workflow.import(ByteArrayInputStream(Config(wallpaper = 1).json().toByteArray())))
        assertEquals(2, active.wallpaper)
    }

    @Test fun exportUsesCurrentActiveConfig() {
        val active = Config(wallpaper = 1)
        val output = ByteArrayOutputStream()
        ConfigWorkflow({ active }) { true }.export(output)
        assertEquals(active, ConfigDocuments.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun fallbackReplacementValidatesBeforeActivation() {
        var active = Config(wallpaper = 2)
        val workflow = ConfigWorkflow({ active }) { next -> active = next; true }
        assertThrows(IllegalArgumentException::class.java) { workflow.replaceBroken("{") }
        assertEquals(2, active.wallpaper)
        assertTrue(workflow.replaceWithDefaults())
        assertEquals(Config(), active)
    }
}

package tech.granet.grove

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ConfigWorkflowTest {
    // Exercises the real document decoder and active repository rather than a
    // retired production wrapper. Each import represents an explicit Apply.
    private class Documents(current: () -> Config, activate: (Config) -> Boolean) {
        private val repository = SettingsRepository(current, { next -> check(activate(next)) }, { next -> check(activate(next)) })
        fun import(input: java.io.InputStream): Boolean {
            val next = ConfigDocuments.read(input)
            return repository.update(replacement = true) { next } is SettingsOutcome.Saved
        }
        fun export(output: java.io.OutputStream) = ConfigDocuments.write(repository.snapshot().config, output)
        fun replaceBroken(text: String): Boolean {
            val next = ConfigStore.parse(text)
            return repository.update(replacement = true) { next } is SettingsOutcome.Saved
        }
        fun replaceWithDefaults(): Boolean = repository.update(replacement = true) { Config() } is SettingsOutcome.Saved
    }
    @Test fun failedImportDoesNotMutateActiveSettings() {
        var active = Config(wallpaper = 2)
        val workflow = Documents({ active }) { next -> active = next; true }
        assertThrows(IllegalArgumentException::class.java) {
            workflow.import(ByteArrayInputStream("""{"version":99,"wallpaper":0,"favorites":[]}""".toByteArray()))
        }
        assertEquals(2, active.wallpaper)
    }

    @Test fun oldVersionImportActivatesOnlyAfterParse() {
        var active = Config(wallpaper = 2)
        val workflow = Documents({ active }) { next -> active = next; true }
        assertTrue(workflow.import(ByteArrayInputStream("""{"version":1,"wallpaper":0,"favorites":[]}""".toByteArray())))
        assertEquals(0, active.wallpaper)
        assertFalse(active.search.contactIndexing)
        assertFalse(active.search.fileIndexing)
    }

    @Test fun activationFailureLeavesCurrentConfigInPlace() {
        val active = Config(wallpaper = 2)
        val workflow = Documents({ active }) { false }
        assertFalse(workflow.import(ByteArrayInputStream(Config(wallpaper = 1).json().toByteArray())))
        assertEquals(2, active.wallpaper)
    }

    @Test fun exportUsesCurrentActiveConfig() {
        val active = Config(wallpaper = 1)
        val output = ByteArrayOutputStream()
        Documents({ active }) { true }.export(output)
        assertEquals(active, ConfigDocuments.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test fun fallbackReplacementValidatesBeforeActivation() {
        var active = Config(wallpaper = 2)
        val workflow = Documents({ active }) { next -> active = next; true }
        assertThrows(IllegalArgumentException::class.java) { workflow.replaceBroken("{") }
        assertEquals(2, active.wallpaper)
        assertTrue(workflow.replaceWithDefaults())
        assertEquals(Config(), active)
    }
    @Test fun successfulImportSurvivesWorkflowRecreationWhenActivationPersists() {
        var stored = Config(wallpaper = 2).json()
        var active = Config.parse(stored)

        fun workflow() = Documents({ active }) { next ->
            stored = next.json()
            active = next
            true
        }

        assertTrue(workflow().import(
            ByteArrayInputStream("""{"version":8,"wallpaper":13,"favorites":[]}""".toByteArray())
        ))
        // Simulate process/activity recreation by rebuilding active state from the persisted document.
        active = Config.parse(stored)
        val recreated = workflow()
        val output = ByteArrayOutputStream()
        recreated.export(output)

        assertEquals(13, active.wallpaper)
        assertEquals("solid-black", org.json.JSONObject(stored).getString("wallpaper"))
        assertEquals(active, ConfigDocuments.read(ByteArrayInputStream(output.toByteArray())))
    }


}

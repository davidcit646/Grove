package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {
    @Test fun independentClientsTransformLatestSnapshotAndRejectStaleReplacement() {
        var disk = Config()
        val repository = SettingsRepository({ disk }, { disk = it }, { disk = it })
        val original = repository.snapshot()
        repository.update { it.copy(homeScreen = it.homeScreen.copy(showClock = false)) }
        repository.update { it.copy(search = it.search.copy(files = true)) }
        assertFalse(repository.snapshot().config.homeScreen.showClock)
        assertTrue(repository.snapshot().config.search.files)
        assertEquals(disk, repository.snapshot().config)
        assertTrue(repository.update(original.revision, true) { original.config } is SettingsOutcome.Conflict)
        assertTrue(disk.search.files)
    }
    @Test fun interveningChangeBackToSameValueStillInvalidatesDraft() {
        var disk = Config()
        val repository = SettingsRepository({ disk }, { disk = it }, { disk = it })
        val base = repository.snapshot()
        repository.update { it.copy(themeMode = ThemeMode.LIGHT) }
        repository.update { it.copy(themeMode = ThemeMode.SYSTEM) }
        assertEquals(base.config, repository.snapshot().config)
        assertTrue(repository.update(base.revision, true) { base.config } is SettingsOutcome.Conflict)
    }
    @Test fun persistenceFailureDoesNotPublishOrAdvanceRevision() {
        var notifications = 0
        val repository = SettingsRepository({ Config() }, { error("disk unavailable") }, {}, published = { notifications++ })
        val before = repository.snapshot()
        assertTrue(repository.update { it.copy(themeMode = ThemeMode.DARK) } is SettingsOutcome.Unavailable)
        assertEquals(before, repository.snapshot()); assertEquals(0, notifications)
    }
    @Test fun invalidProgrammaticMutationNeverTouchesStorage() {
        var writes = 0
        val repository = SettingsRepository({ Config() }, { writes++ }, { writes++ })
        assertTrue(repository.update { it.copy(favorites = listOf("invalid")) } is SettingsOutcome.Invalid)
        assertEquals(0, writes); assertTrue(repository.snapshot().config.favorites.isEmpty())
    }
    @Test fun replacementUsesActivationAndPublicationFollowsDurableWrite() {
        val events = mutableListOf<String>()
        val repository = SettingsRepository({ Config() }, { events.add("save") }, { events.add("activate") },
            published = { events.add("publish") })
        assertTrue(repository.update(replacement = true) { it.copy(themeMode = ThemeMode.LIGHT) } is SettingsOutcome.Saved)
        assertEquals(listOf("activate", "publish"), events)
    }
}

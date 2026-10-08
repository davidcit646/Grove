package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

/** Exercises the live publication authority after retiring ConfigTransaction. */
class ConfigTransactionTest {
    @Test fun persistenceFailureKeepsActiveStateAndStopsPublication() {
        var notifications = 0
        val repository = SettingsRepository({ Config() }, { error("disk") }, {}, published = { notifications++ })
        val before = repository.snapshot()
        assertTrue(repository.update { it.copy(themeMode = ThemeMode.DARK) } is SettingsOutcome.Unavailable)
        assertEquals(before, repository.snapshot()); assertEquals(0, notifications)
    }
    @Test fun persistenceCompletesBeforePublication() {
        val events = mutableListOf<String>()
        val repository = SettingsRepository({ Config() }, { events += "persist" }, {}, published = { events += "publish" })
        assertTrue(repository.update { it.copy(themeMode = ThemeMode.DARK) } is SettingsOutcome.Saved)
        assertEquals(listOf("persist", "publish"), events)
    }
    @Test fun publicationInvariantIsNotReportedAsPersistenceFailure() {
        val repository = SettingsRepository({ Config() }, {}, {}, published = { error("invariant") })
        try { repository.update { it.copy(themeMode = ThemeMode.DARK) }; fail() }
        catch (_: IllegalStateException) { assertEquals(ThemeMode.DARK, repository.snapshot().config.themeMode) }
    }
}

package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupCoordinatorTest {
    private fun state(
        contacts: Boolean = false,
        contactsGranted: Boolean = false,
        lastContacts: Long = 0,
        indexingContacts: Boolean = false,
        contactLoadFailed: Boolean = false,
        files: Boolean = false,
        filesGranted: Boolean = false,
        hasFiles: Boolean = false,
        indexingFiles: Boolean = false,
    ) = StartupCoordinator.Snapshot(
        contacts, contactsGranted, lastContacts, indexingContacts, contactLoadFailed,
        files, filesGranted, hasFiles, indexingFiles,
    )

    @Test fun coldStartLoadsOnlyEnabledGrantedSources() {
        assertEquals(StartupCoordinator.Plan(loadApps = true), StartupCoordinator.coldStart(state()))
        assertEquals(
            StartupCoordinator.Plan(loadApps = true, refreshContacts = true, indexFiles = true),
            StartupCoordinator.coldStart(state(contacts = true, contactsGranted = true,
                files = true, filesGranted = true)),
        )
        assertEquals(StartupCoordinator.Plan(loadApps = true), StartupCoordinator.coldStart(
            state(contacts = true, files = true)),
        )
    }

    @Test fun resumeRefreshesStaleContactsAndIndexesOnlyMissingFiles() {
        val recent = state(contacts = true, contactsGranted = true, lastContacts = 1_000,
            files = true, filesGranted = true, hasFiles = true)
        assertEquals(StartupCoordinator.Plan(), StartupCoordinator.resume(recent, 2_000))
        assertEquals(StartupCoordinator.Plan(refreshContacts = true),
            StartupCoordinator.resume(recent, 1_000 + 15 * 60_000L + 1))
        assertEquals(StartupCoordinator.Plan(), StartupCoordinator.resume(
            state(contacts = true, contactsGranted = true, indexingContacts = true),
            15 * 60_000L + 1))
        assertEquals(StartupCoordinator.Plan(refreshContacts = true),
            StartupCoordinator.resume(state(contacts = true, contactsGranted = true,
                lastContacts = 1_000, contactLoadFailed = true), 2_000))
        assertEquals(StartupCoordinator.Plan(indexFiles = true), StartupCoordinator.resume(
            state(files = true, filesGranted = true), 2_000))
        assertEquals(StartupCoordinator.Plan(), StartupCoordinator.resume(
            state(files = true, filesGranted = true, indexingFiles = true), 2_000))
    }

    @Test fun resumeClearsRevokedFileAccessAndContacts() {
        assertEquals(StartupCoordinator.Plan(refreshContacts = true, clearFiles = true),
            StartupCoordinator.resume(state(contacts = true, files = true, hasFiles = true), 2_000))
        assertEquals(StartupCoordinator.Plan(clearFiles = true),
            StartupCoordinator.resume(state(files = true, indexingFiles = true), 2_000))
    }
}

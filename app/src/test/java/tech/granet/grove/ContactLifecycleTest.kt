package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class ContactLifecycleTest {
    @Test fun ownWorkerComponentCyclesCannotReloadCatalogue() {
        val reloads = mutableListOf<String>()
        repeat(100) { LauncherPackageEvents.changed("grove.test", "grove.test", reloads::add) }
        assertTrue(reloads.isEmpty())
        listOf("installed.app", "updated.app", "removed.app").forEach {
            LauncherPackageEvents.changed(it, "grove.test", reloads::add)
        }
        assertEquals(listOf("installed.app", "updated.app", "removed.app"), reloads)
    }

    @Test fun elapsedDeadlineIncludesQueryAndRowsEvenWithoutValidContacts() {
        var clock = 100L
        val budget = ContactScanBudget(3, 2500) { clock }
        repeat(3) { assertFalse(budget.exhausted()); budget.visited() }
        assertTrue(budget.exhausted())
        val slowProvider = ContactScanBudget(100_000, 2500) { clock }
        clock += 2500
        assertTrue(slowProvider.expired())
        assertEquals(0, slowProvider.rows)
    }

    @Test fun usableEmptyScanDoesNotConsumeBudgetOrClaimPartial() {
        val budget = ContactScanBudget(50_000, 2500) { 100L }
        assertFalse(budget.exhausted())
        assertEquals(0, budget.rows)
    }

    @Test fun validatedCacheFreshnessExpiresWithoutReloadAndRejectsFutureOrInvalidatedData() {
        val cache = IndexMetadata(IndexValidity.AVAILABLE, 1_000)
        assertTrue(cache.fresh("contacts", 901_000))
        assertFalse(cache.fresh("contacts", 901_001))
        assertTrue(cache.fresh("files", 901_001))
        assertFalse(cache.fresh("contacts", 999))
        assertFalse(cache.copy(invalidated = true).fresh("contacts", 1_001))
        assertFalse(cache.copy(validity = IndexValidity.CORRUPT).fresh("contacts", 1_001))
        assertFalse(IndexMetadata().fresh("contacts", 1_001))
        assertTrue(cache.copy(partial = true).fresh("contacts", 1_001))
    }

    @Test fun automaticCausesNeverInheritManualRefreshDelayPolicy() {
        for (cause in IndexRefreshCause.values()) {
            assertEquals(if (cause == IndexRefreshCause.PROVIDER_CHANGE) 29_900L else 0L,
                IndexRefreshRequests.delayMillis(cause, 100_000, 100_100))
        }
    }
}

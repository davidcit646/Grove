package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class GroveErrorRegistryTest {
    @Test fun codesAreUniqueBoundedAndOwnedByTheirReservedRange() {
        val all = GroveErrorRegistry.all
        assertEquals(all.size, all.map { it.code }.toSet().size)
        assertTrue(all.all { it.code in 100..699 })
        assertTrue(all.all { it.code in it.owner.range })
    }

    @Test fun wallpaperDiagnosticsUseAssignedGwsFormatAndAreUnique() {
        val gws = GroveErrorRegistry.all.mapNotNull { it.gws }
        assertEquals(gws.size, gws.toSet().size)
        assertTrue(gws.isNotEmpty())
        assertTrue(gws.all { it.matches(Regex("GWS-(UI|CACHE|NETWORK|APPLY|UX|READ|WRITE)-[0-9]{2}")) })
        assertTrue(GroveErrorRegistry.all.filter { it.gws != null }.all { it.owner == GroveErrorOwner.UI_UX })
    }

    @Test fun fileAndContactFailuresStayInTheirDedicatedRanges() {
        assertEquals(GroveErrorOwner.GFI, GroveErrorRegistry.FILE_SEARCH.owner)
        assertEquals(GroveErrorOwner.GCI, GroveErrorRegistry.CONTACT_SEARCH.owner)
        assertTrue(GroveErrorRegistry.FILE_SEARCH.code in 500..599)
        assertTrue(GroveErrorRegistry.CONTACT_SEARCH.code in 600..699)
    }

    @Test fun shippedCodesResolveBackToTheirRegistryEntry() {
        GroveErrorRegistry.all.forEach { error ->
            assertEquals(error, GroveErrorRegistry.byCode(error.code))
            assertTrue(error.feature.isNotBlank())
            assertTrue(error.summary.isNotBlank())
        }
    }
    @Test fun searchTutorialFailuresHaveDistinctAssignedCodesAndSafeContinueRoutes() {
        val errors = listOf(GroveErrorRegistry.SEARCH_TUTORIAL_PRESENTATION,
            GroveErrorRegistry.SEARCH_TUTORIAL_STATE, GroveErrorRegistry.SEARCH_TUTORIAL_COMPLETION)
        assertEquals(listOf(314, 411, 412), errors.map { it.code })
        assertEquals(GroveErrorOwner.UI_UX, errors[0].owner)
        assertTrue(errors.drop(1).all { it.owner == GroveErrorOwner.SYSTEM })
        errors.forEach {
            assertEquals(it, GroveErrorRegistry.byCode(it.code))
            assertEquals(ErrorSeverity.DEGRADE, it.severity)
            assertEquals("Continue", GroveErrorRouting.route(it, false).dismissLabel)
            assertTrue(it.summary.contains("Search is still available"))
        }
    }

}

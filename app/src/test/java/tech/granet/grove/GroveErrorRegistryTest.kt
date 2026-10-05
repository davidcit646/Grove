package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class GroveErrorRegistryTest {
    @Test fun codesAndGwsIdentifiersAreUniqueAndBounded() {
        val all = GroveErrorRegistry.all
        assertEquals(all.size, all.map { it.code }.toSet().size)
        assertEquals(all.size, all.map { it.gws }.toSet().size)
        assertTrue(all.all { it.code in 100..699 })
        assertTrue(all.all { it.gws.matches(Regex("GWS-[a-z]+-[a-z0-9-]+")) })
    }

    @Test fun shippedCodesResolveBackToTheirRegistryEntry() {
        GroveErrorRegistry.all.forEach { error ->
            assertEquals(error, GroveErrorRegistry.byCode(error.code))
            assertTrue(error.feature.isNotBlank())
            assertTrue(error.summary.isNotBlank())
        }
    }
}

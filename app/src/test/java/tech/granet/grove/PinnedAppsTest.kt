package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedAppsTest {
    private val apps = listOf("a/.A", "b/.B", "c/.C", "d/.D")

    @Test fun shiftEarlierAndLater() {
        assertEquals(listOf("a/.A", "c/.C", "b/.B", "d/.D"), PinnedApps.shift(apps, "c/.C", -1))
        assertEquals(listOf("a/.A", "c/.C", "b/.B", "d/.D"), PinnedApps.shift(apps, "b/.B", 1))
    }

    @Test fun shiftStopsAtEdges() {
        assertEquals(apps, PinnedApps.shift(apps, "a/.A", -1))
        assertEquals(apps, PinnedApps.shift(apps, "d/.D", 1))
    }

    @Test fun dragMovePlacesItemAtTarget() {
        assertEquals(listOf("a/.A", "d/.D", "b/.B", "c/.C"), PinnedApps.moveTo(apps, "d/.D", "b/.B"))
    }

    @Test fun invalidMoveDoesNothing() {
        assertEquals(apps, PinnedApps.moveTo(apps, "missing/.X", "b/.B"))
        assertEquals(apps, PinnedApps.moveTo(apps, "b/.B", "b/.B"))
    }
    @Test fun moveForwardReachesLastSlot() {
        assertEquals(listOf("b/.B", "c/.C", "d/.D", "a/.A"), PinnedApps.moveTo(apps, "a/.A", "d/.D"))
        assertEquals(listOf("b/.B", "a/.A", "c/.C", "d/.D"), PinnedApps.moveTo(apps, "a/.A", "b/.B"))
    }

    @Test fun allDropPairsPreserveOthersAndPersistOrder() {
        for (source in apps) for (target in apps) {
            val next = PinnedApps.moveTo(apps, source, target)
            assertEquals(apps.indexOf(target), next.indexOf(source))
            assertEquals(apps.filter { it != source }, next.filter { it != source })
            assertEquals(next, Config.parse(Config(favorites = next).json()).favorites)
        }
    }
}

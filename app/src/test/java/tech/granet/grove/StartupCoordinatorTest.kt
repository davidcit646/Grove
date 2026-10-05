package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupCoordinatorTest {
    @Test fun homeLoadsBeforeOptionalIndexes() {
        assertEquals(StartupCoordinator.Plan(loadApps = true, reconcileIndexes = true), StartupCoordinator.coldStart())
        assertEquals(StartupCoordinator.Plan(reconcileIndexes = true), StartupCoordinator.resume())
    }
}

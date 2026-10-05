package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroveErrorRoutingTest {
    @Test fun continueWithoutActionStaysScoped() {
        val route = GroveErrorRouting.route(GroveErrorRegistry.CONTACT_SEARCH, false)
        assertEquals("Continue", route.dismissLabel)
        assertNull(route.actionLabel)
    }

    @Test fun recoverWithActionOffersRecovery() {
        val route = GroveErrorRouting.route(GroveErrorRegistry.CONFIG_PERSIST, true)
        assertEquals("Not now", route.dismissLabel)
        assertEquals("Recover", route.actionLabel)
    }

    @Test fun stoppedOperationWithActionOffersRetryWithoutClaimingSuccess() {
        val route = GroveErrorRouting.route(GroveErrorRegistry.WALLPAPER_APPLY, true)
        assertEquals("Close", route.dismissLabel)
        assertEquals("Retry", route.actionLabel)
    }
}

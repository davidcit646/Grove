package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class CoreRecoveryPolicyTest {
    @Test fun everyCoreFailureIsRetryableAndHasHomeSettingsEscape() {
        CoreRecoveryReason.entries.forEach { reason ->
            val state = CoreRecoveryPolicy.forReason(reason)
            assertEquals(reason, state.reason)
            assertTrue(state.retryable)
            assertTrue(state.settingsEscape)
            assertTrue(state.detail.isNotBlank())
        }
    }

    @Test fun appCatalogFailureExplainsInstalledAppFailure() {
        val state = CoreRecoveryPolicy.forReason(CoreRecoveryReason.APP_CATALOG)
        assertTrue(state.detail.contains("installed app list"))
    }

    @Test fun configFailurePromisesSavedSettingsAreNotErased() {
        val state = CoreRecoveryPolicy.forReason(CoreRecoveryReason.CONFIG)
        assertTrue(state.detail.contains("not been erased"))
    }
}

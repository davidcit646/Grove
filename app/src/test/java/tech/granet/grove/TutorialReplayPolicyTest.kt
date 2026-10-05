package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialReplayPolicyTest {
    @Test fun noPendingRequestDoesNothing() {
        assertEquals(TutorialReplayDecision.NONE,
            TutorialReplayPolicy.decide(false, setupShowing = false, appsAvailable = true))
    }

    @Test fun repeatedCallbackCannotStackSetup() {
        assertEquals(TutorialReplayDecision.NONE,
            TutorialReplayPolicy.decide(true, setupShowing = true, appsAvailable = true))
    }

    @Test fun unavailableCatalogDefersWithoutClearingRequest() {
        assertEquals(TutorialReplayDecision.DEFER,
            TutorialReplayPolicy.decide(true, setupShowing = false, appsAvailable = false))
    }

    @Test fun onePendingRequestStartsWhenReady() {
        assertEquals(TutorialReplayDecision.START,
            TutorialReplayPolicy.decide(true, setupShowing = false, appsAvailable = true))
    }
    @Test fun replaySkipNeverSeedsFavoritesIntoExistingSetup() {
        assertEquals(false, TutorialReplayPolicy.shouldSeedFavoritesOnSkip(
            setupPreviouslyCompleted = true,
            favoritesEmpty = true,
        ))
    }

    @Test fun genuineFirstRunSkipMaySeedOnlyAnEmptyFavoritesList() {
        assertEquals(true, TutorialReplayPolicy.shouldSeedFavoritesOnSkip(
            setupPreviouslyCompleted = false,
            favoritesEmpty = true,
        ))
        assertEquals(false, TutorialReplayPolicy.shouldSeedFavoritesOnSkip(
            setupPreviouslyCompleted = false,
            favoritesEmpty = false,
        ))
    }

}

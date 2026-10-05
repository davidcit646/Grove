package tech.granet.grove

internal enum class TutorialReplayDecision { NONE, DEFER, START }

internal object TutorialReplayPolicy {
    fun decide(pending: Boolean, setupShowing: Boolean, appsAvailable: Boolean): TutorialReplayDecision = when {
        !pending || setupShowing -> TutorialReplayDecision.NONE
        !appsAvailable -> TutorialReplayDecision.DEFER
        else -> TutorialReplayDecision.START
    }

    fun shouldSeedFavoritesOnSkip(setupPreviouslyCompleted: Boolean, favoritesEmpty: Boolean): Boolean =
        !setupPreviouslyCompleted && favoritesEmpty
}

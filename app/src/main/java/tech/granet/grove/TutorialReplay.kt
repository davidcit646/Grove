package tech.granet.grove

internal enum class TutorialReplayDecision { NONE, DEFER, START }

internal object TutorialReplayPolicy {
    fun decide(pending: Boolean, setupShowing: Boolean, appsAvailable: Boolean): TutorialReplayDecision {
        PortablePolicy.ruleInt("tutorialReplay", 0..2, "pending" to pending, "setup" to setupShowing, "apps" to appsAvailable)
            ?.let { return TutorialReplayDecision.entries[it] }
        return when {
        !pending || setupShowing -> TutorialReplayDecision.NONE
        !appsAvailable -> TutorialReplayDecision.DEFER
        else -> TutorialReplayDecision.START
        }
    }

    fun shouldSeedFavoritesOnSkip(setupPreviouslyCompleted: Boolean, favoritesEmpty: Boolean): Boolean =
        PortablePolicy.ruleBool("seedFavorites", "completed" to setupPreviouslyCompleted, "empty" to favoritesEmpty)
            ?: (!setupPreviouslyCompleted && favoritesEmpty)
}

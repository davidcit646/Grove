package tech.granet.grove

/** Session authority for optional presentation and request-scoped suppression. */
internal class SearchTutorialSession(
    var state: SearchTutorialState = SearchTutorialState(),
    var request: Long = 0L,
) {
    fun shouldPresent(savedVersion: Int, replay: Boolean, nextRequest: Long): Boolean {
        if (nextRequest != request) state.sessionSuppressed = false
        // Capture the request before presentation, including presentations that fail.
        request = nextRequest
        return SearchTutorialState.shouldShow(savedVersion, replay, state.sessionSuppressed)
    }

    fun present(prepareUnderlay: () -> Unit, render: () -> Unit,
                recover: (Exception) -> Unit): Boolean {
        return try {
            // Leave a stable Home behind the guide, including if it is dismissed incomplete.
            prepareUnderlay()
            render()
            true
        } catch (error: Exception) {
            suppress()
            recover(error)
            false
        }
    }

    fun suppress() { state.sessionSuppressed = true }
}

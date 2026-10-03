package tech.granet.grove

/** Pure decisions about which launcher data should be loaded at a lifecycle boundary. */
internal object StartupCoordinator {
    private const val CONTACT_REFRESH_INTERVAL_MS = 15 * 60_000L

    data class Snapshot(
        val contactSearchEnabled: Boolean,
        val contactsGranted: Boolean,
        val lastContactRefreshMs: Long,
        val fileSearchEnabled: Boolean,
        val filesGranted: Boolean,
        val hasFiles: Boolean,
        val indexingFiles: Boolean,
    )

    data class Plan(
        val loadApps: Boolean = false,
        val refreshContacts: Boolean = false,
        val indexFiles: Boolean = false,
        val clearFiles: Boolean = false,
    )

    fun coldStart(state: Snapshot): Plan = Plan(
        loadApps = true,
        refreshContacts = state.contactSearchEnabled && state.contactsGranted,
        indexFiles = state.fileSearchEnabled && state.filesGranted,
    )

    fun resume(state: Snapshot, nowMs: Long): Plan = Plan(
        refreshContacts = state.contactSearchEnabled &&
            (!state.contactsGranted || nowMs - state.lastContactRefreshMs > CONTACT_REFRESH_INTERVAL_MS),
        indexFiles = state.fileSearchEnabled && state.filesGranted && !state.hasFiles && !state.indexingFiles,
        clearFiles = state.fileSearchEnabled && !state.filesGranted && (state.hasFiles || state.indexingFiles),
    )
}

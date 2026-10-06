package tech.granet.grove

/** Provider-only preferences never authorize protected-source reconciliation. */
internal object SearchSettingsEffects {
    fun contacts(before: SearchSettings, after: SearchSettings) =
        before.contacts != after.contacts || before.contactIndexing != after.contactIndexing
    fun files(before: SearchSettings, after: SearchSettings) =
        before.files != after.files || before.fileIndexing != after.fileIndexing
    fun protectedSources(before: SearchSettings, after: SearchSettings) = contacts(before, after) || files(before, after)
    fun reconcile(before: SearchSettings, after: SearchSettings, contact: () -> Boolean, file: () -> Boolean): Boolean {
        val contactsReady = !contacts(before, after) || contact()
        val filesReady = !files(before, after) || file()
        return contactsReady && filesReady
    }
}


package tech.granet.grove

/** Provider-only preferences never authorize protected-source reconciliation. */
internal object SearchSettingsEffects {
    fun contacts(before: SearchSettings, after: SearchSettings) =
        PortablePolicy.ruleBool("settingsEffects", "beforeSearch" to before.contacts, "afterSearch" to after.contacts,
            "beforeIndex" to before.contactIndexing, "afterIndex" to after.contactIndexing)
            ?: (before.contacts != after.contacts || before.contactIndexing != after.contactIndexing)
    fun files(before: SearchSettings, after: SearchSettings) =
        PortablePolicy.ruleBool("settingsEffects", "beforeSearch" to before.files, "afterSearch" to after.files,
            "beforeIndex" to before.fileIndexing, "afterIndex" to after.fileIndexing)
            ?: (before.files != after.files || before.fileIndexing != after.fileIndexing)
    fun protectedSources(before: SearchSettings, after: SearchSettings) = contacts(before, after) || files(before, after)
    fun reconcile(before: SearchSettings, after: SearchSettings, contact: () -> Boolean, file: () -> Boolean): Boolean {
        val contactsReady = !contacts(before, after) || contact()
        val filesReady = !files(before, after) || file()
        return contactsReady && filesReady
    }
}

package tech.granet.grove

/** New-install defaults are separate from migration and corrupt-config recovery. */
internal object SetupDefaults {
    fun configuration(hasConfig: Boolean, initialized: Boolean, setupCompleted: Boolean): Config =
        if (PortablePolicy.ruleBool("freshInstall", "config" to hasConfig, "initialized" to initialized, "completed" to setupCompleted)
            ?: (!hasConfig && !initialized && !setupCompleted))
            Config(search = SearchSettings(contactIndexing = true, fileIndexing = true))
        else Config()
}

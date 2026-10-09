package tech.granet.grove

/** A document may replace only the exact settings revision reviewed by the user. */
internal object ConfigDocumentGate {
    fun canActivate(startedWith: SettingsSnapshot, current: SettingsSnapshot): Boolean =
        PortablePolicy.ruleBool("configCurrent", "same" to (startedWith == current)) ?: (startedWith == current)
}

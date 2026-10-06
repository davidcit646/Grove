package tech.granet.grove

/** The only active Config authority. No Android host, View, or feature operation lives here. */
internal data class SettingsSnapshot(val config: Config, val revision: Long)
internal sealed interface SettingsOutcome {
    data class Saved(val snapshot: SettingsSnapshot) : SettingsOutcome
    data class Invalid(val reason: String) : SettingsOutcome
    data class Conflict(val snapshot: SettingsSnapshot) : SettingsOutcome
    data class Unavailable(val error: Exception) : SettingsOutcome
}
internal class SettingsRepository(
    private val load: () -> Config,
    private val save: (Config) -> Unit,
    private val activate: (Config) -> Unit,
    private val validate: (Config) -> Config = { ConfigStore.parse(it.json()) },
    private val published: (SettingsSnapshot) -> Unit = {},
) {
    private var state: SettingsSnapshot? = null
    @Synchronized fun snapshot(): SettingsSnapshot = state ?: SettingsSnapshot(load(), 0).also { state = it }
    @Synchronized fun update(expected: Long? = null, replacement: Boolean = false,
                             change: (Config) -> Config): SettingsOutcome {
        val before = try { snapshot() } catch (error: Exception) { return SettingsOutcome.Unavailable(error) }
        if (expected != null && expected != before.revision) return SettingsOutcome.Conflict(before)
        val next = try { validate(change(before.config)) }
            catch (error: IllegalArgumentException) { return SettingsOutcome.Invalid(error.message ?: "Invalid setting") }
        try { if (replacement) activate(next) else save(next) }
        catch (error: Exception) { return SettingsOutcome.Unavailable(error) }
        // Publication is after confirmed persistence. Effects run in their own owners, never here.
        val committed = SettingsSnapshot(next, before.revision + 1)
        state = committed
        published(committed)
        return SettingsOutcome.Saved(committed)
    }
}

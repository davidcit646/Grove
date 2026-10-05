package tech.granet.grove

import java.io.InputStream
import java.io.OutputStream

/**
 * Configuration document/recovery decisions without Activity or View ownership.
 * Parsing and I/O complete before callers may publish a new active Config.
 */
internal class ConfigWorkflow(
    private val current: () -> Config,
    private val activate: (Config) -> Boolean,
) {
    fun import(input: InputStream): Boolean {
        val parsed = ConfigDocuments.read(input)
        return activate(parsed)
    }

    fun export(output: OutputStream) {
        ConfigDocuments.write(current(), output)
    }

    fun replaceWithDefaults(): Boolean = activate(Config())

    fun replaceBroken(text: String): Boolean = activate(ConfigStore.parse(text))
}

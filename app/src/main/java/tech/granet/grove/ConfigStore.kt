package tech.granet.grove

import android.content.SharedPreferences

/** Keeps a damaged custom configuration intact while Grove runs on a separate fallback. */
class ConfigStore(private val prefs: SharedPreferences) {
    companion object {
        fun parse(text: String): Config = try {
            Config.parse(text)
        } catch (error: Exception) {
            throw IllegalArgumentException(error.message ?: "Invalid configuration", error)
        }
    }
    var brokenCustomConfig: String? = null
        private set
    private var usingFallback = false

    fun load(): Config {
        val raw = prefs.getString("config", null)
        usingFallback = prefs.getBoolean("using_fallback_config", false)
        if (usingFallback) {
            brokenCustomConfig = prefs.getString("broken_config", raw)
            return runCatching { parse(prefs.getString("fallback_config", "") ?: "") }
                .getOrDefault(Config())
        }
        if (raw.isNullOrBlank()) return SetupDefaults.configuration(
            prefs.contains("config"), prefs.contains("initialized"), prefs.contains("setup_complete"))
        return runCatching { parse(raw) }.getOrElse {
            usingFallback = true
            brokenCustomConfig = raw
            val safe = Config()
            check(prefs.edit().putString("broken_config", raw).putString("fallback_config", safe.json())
                .putBoolean("using_fallback_config", true).commit()) { "Could not persist recovery configuration" }
            safe
        }
    }

    fun save(config: Config) {
        val editor = prefs.edit()
        if (usingFallback) editor.putString("fallback_config", config.json())
        else editor.putString("config", config.json())
        check(editor.commit()) { "Could not save configuration" }
    }

    fun activate(config: Config) {
        check(prefs.edit().putString("config", config.json()).remove("broken_config")
            .remove("fallback_config").remove("using_fallback_config").commit()) {
            "Could not activate configuration"
        }
        usingFallback = false
        brokenCustomConfig = null
    }
}

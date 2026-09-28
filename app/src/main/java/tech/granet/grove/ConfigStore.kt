package tech.granet.grove

import android.content.SharedPreferences

/** Keeps a damaged custom configuration intact while Grove runs on a separate fallback. */
class ConfigStore(private val prefs: SharedPreferences) {
    companion object {
        fun parse(text: String): Config = try {
            require(text.length <= 65_536 && text.toByteArray(Charsets.UTF_8).size <= 65_536) {
                "Configuration exceeds 64 KB"
            }
            var depth = 0
            var quoted = false
            var escaped = false
            for (character in text) {
                if (quoted) {
                    if (escaped) escaped = false
                    else if (character == '\\') escaped = true
                    else if (character == '"') quoted = false
                } else when (character) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 64) { "Configuration is nested too deeply" } }
                    '}', ']' -> depth--
                }
            }
            CoreBridge.configProblem(text)?.let { throw IllegalArgumentException(it) }
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
        if (raw.isNullOrBlank()) return Config()
        return runCatching { parse(raw) }.getOrElse {
            usingFallback = true
            brokenCustomConfig = raw
            val safe = Config()
            prefs.edit().putString("broken_config", raw).putString("fallback_config", safe.json())
                .putBoolean("using_fallback_config", true).apply()
            safe
        }
    }

    fun save(config: Config) {
        val editor = prefs.edit()
        if (usingFallback) editor.putString("fallback_config", config.json())
        else editor.putString("config", config.json())
        editor.apply()
    }

    fun activate(config: Config) {
        usingFallback = false
        brokenCustomConfig = null
        prefs.edit().putString("config", config.json()).remove("broken_config")
            .remove("fallback_config").remove("using_fallback_config").apply()
    }
}

package tech.granet.grove

/** Persisted user intent; Android owns wallpaper colors and system night state. */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark"),
    WALLPAPER("wallpaper", "Wallpaper colors");
    companion object {
        fun parse(value: String): ThemeMode = entries.firstOrNull { it.id == value }
            ?: throw IllegalArgumentException("Unknown theme mode")
    }
}

internal object PresentationPolicy {
    fun wallpaperColors(mode: ThemeMode, legacyColors: Boolean): Boolean =
        mode == ThemeMode.WALLPAPER || (mode == ThemeMode.SYSTEM && legacyColors)
    fun sourceChanged(which: Int, homeFlag: Int): Boolean = which and homeFlag != 0
}

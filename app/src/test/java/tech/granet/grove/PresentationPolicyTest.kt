package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class PresentationPolicyTest {
    @Test fun everyThemePersistsThroughExportAndRecreation() {
        ThemeMode.entries.forEach { mode ->
            val original = Config(themeMode = mode, wallpaper = 13)
            assertEquals(original, ConfigStore.parse(original.json()))
        }
    }
    @Test fun legacyConfigurationKeepsSystemModeAndExistingColorPreference() {
        val migrated = ConfigStore.parse("""{"version":9,"wallpaper":"solid-black","favorites":[],"homeScreen":{"useWallpaperButtonColors":true}}""")
        assertEquals(ThemeMode.SYSTEM, migrated.themeMode)
        assertTrue(PresentationPolicy.wallpaperColors(migrated.themeMode, migrated.homeScreen.useWallpaperButtonColors))
        assertEquals(migrated, ConfigStore.parse(migrated.json()))
    }
    @Test fun explicitLightAndDarkOverrideLegacyWallpaperColorSwitch() {
        assertFalse(PresentationPolicy.wallpaperColors(ThemeMode.LIGHT, true))
        assertFalse(PresentationPolicy.wallpaperColors(ThemeMode.DARK, true))
        assertTrue(PresentationPolicy.wallpaperColors(ThemeMode.WALLPAPER, false))
    }
    @Test fun invalidMissingOrNonStringThemeFailsClosed() {
        listOf("null", "42", "\"unknown\"").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ConfigStore.parse("""{"version":10,"wallpaper":"solid-black","favorites":[],"themeMode":$value}""")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigStore.parse("""{"version":10,"wallpaper":"solid-black","favorites":[]}""")
        }
    }
    @Test fun lockOnlyColorChangesDoNotInvalidateHomeButBothDoes() {
        assertFalse(PresentationPolicy.sourceChanged(2, 1))
        assertTrue(PresentationPolicy.sourceChanged(1, 1))
        assertTrue(PresentationPolicy.sourceChanged(3, 1))
    }
}

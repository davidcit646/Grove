package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class WallpaperRegistryTest {
    @Test fun sourceIdsAndLegacyIndexesAreUniqueAndStable() {
        val sources = WallpaperArt.sources
        assertEquals(15, sources.size)
        assertEquals(sources.size, sources.map { it.id }.toSet().size)
        assertEquals((0..14).toList(), sources.map { it.legacyIndex }.sorted())
        sources.forEach { source ->
            assertEquals(source.legacyIndex, WallpaperArt.indexForId(source.id))
            assertEquals(source, WallpaperArt.source(source.legacyIndex))
        }
    }

    @Test fun bundledCommonsSourcesHavePackagedResources() {
        val curated = WallpaperArt.sources.filter { it.kind == WallpaperKind.COMMONS }
        assertEquals(10, curated.size)
        assertTrue(curated.all { it.resourceId != null && it.resourceId != 0 })
        assertTrue(curated.all { it.sourcePage?.startsWith("https://commons.wikimedia.org/wiki/File:") == true })
    }

    @Test fun blackAndCustomHaveDedicatedSources() {
        assertEquals(WallpaperKind.SOLID_BLACK, WallpaperArt.source(13)?.kind)
        assertEquals(WallpaperKind.CUSTOM, WallpaperArt.source(14)?.kind)
    }

    @Test fun configAcceptsNewWallpaperSlots() {
        assertEquals(13, Config.parse(Config(wallpaper = 13).json()).wallpaper)
        assertEquals(14, Config.parse(Config(wallpaper = 14).json()).wallpaper)
    }
}

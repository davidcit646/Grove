package tech.granet.grove

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GridPolicyTest {
    @Test fun everyGridRoundTripsAndEveryOverflowItemRemainsReachable() {
        val items = (0..237).toList()
        for (columns in 1..10) for (rows in 1..10) {
            val grid = IconGrid(columns, rows)
            val config = Config(homeGrid = grid, drawerGrid = grid, favorites = listOf("a/.B"))
            assertEquals(config, ConfigStore.parse(config.json()))
            val pages = (0 until GridPolicy.pageCount(items.size, grid)).flatMap { GridPolicy.items(items, it, grid) }
            assertEquals(items, pages)
        }
    }
    @Test fun legacySizingAndUnrelatedChoicesSurviveMigration() {
        val json = JSONObject(Config(search = SearchSettings(files = true), themeMode = ThemeMode.DARK).json()).put("version", 10)
        json.remove("homeGrid"); json.remove("drawerGrid")
        val config = Config.parse(json.toString())
        assertNull(config.homeGrid); assertNull(config.drawerGrid)
        assertEquals(4, GridPolicy.columns(null, 599)); assertEquals(6, GridPolicy.columns(null, 600))
        assertTrue(config.search.files); assertEquals(ThemeMode.DARK, config.themeMode)
    }
    @Test fun invalidGridTypesAndBoundsAreRejected() {
        for (bad in listOf(0, 11, -1, 1.5, "4", true, JSONObject.NULL)) {
            val json = JSONObject(Config().json()).put("homeGrid", JSONObject().put("columns", bad).put("rows", 2))
            try { ConfigStore.parse(json.toString()); fail("Accepted invalid grid $bad") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun pageClampsAfterShrinkAndEmptyInventoryIsUsable() {
        assertEquals(0, GridPolicy.page(99, 0, IconGrid(10, 10)))
        assertEquals(2, GridPolicy.page(99, 3, IconGrid(1, 1)))
        assertEquals(emptyList<Int>(), GridPolicy.items(emptyList<Int>(), -1, IconGrid(1, 1)))
    }
}

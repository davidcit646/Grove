package tech.granet.grove

/** Null means legacy automatic layout; custom rows are a page capacity, never data truncation. */
data class IconGrid(val columns: Int, val rows: Int) {
    init { require(columns in 1..10 && rows in 1..10) { "Grid columns and rows must be 1–10" } }
    val capacity: Int get() = columns * rows
    private fun native(count: Int, page: Int, grid: IconGrid?): org.json.JSONObject? =
        (PortablePolicy.value("grid", org.json.JSONObject().put("count", count).put("page", page)
            .put("capacity", grid?.capacity ?: 0)) as? org.json.JSONObject)?.takeIf {
                val pages = it.optLong("pages", -1); val current = it.optLong("page", -1)
                pages in 1..Int.MAX_VALUE.toLong() && current in 0 until pages && it.optLong("start", -1) >= 0
            }
}

internal object GridPolicy {
    fun columns(grid: IconGrid?, widthDp: Int): Int = grid?.columns ?: if (widthDp >= 600) 6 else 4
    fun pageCount(count: Int, grid: IconGrid?): Int = native(count, 0, grid)?.getInt("pages")
        ?: if (grid == null) 1 else maxOf(1L, (count.toLong().coerceAtLeast(0) + grid.capacity - 1) / grid.capacity).toInt()
    fun page(page: Int, count: Int, grid: IconGrid?): Int = native(count, page, grid)?.getInt("page") ?: page.coerceIn(0, pageCount(count, grid) - 1)
    fun <T> items(items: List<T>, page: Int, grid: IconGrid?): List<T> =
        if (grid == null) items else items.drop(page(page, items.size, grid) * grid.capacity).take(grid.capacity)
    private fun native(count: Int, page: Int, grid: IconGrid?): org.json.JSONObject? =
        (PortablePolicy.value("grid", org.json.JSONObject().put("count", count).put("page", page)
            .put("capacity", grid?.capacity ?: 0)) as? org.json.JSONObject)?.takeIf {
                val pages = it.optLong("pages", -1); val current = it.optLong("page", -1)
                pages in 1..Int.MAX_VALUE.toLong() && current in 0 until pages && it.optLong("start", -1) >= 0
            }
}


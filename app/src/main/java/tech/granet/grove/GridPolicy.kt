package tech.granet.grove

/** Null means legacy automatic layout; custom rows are a page capacity, never data truncation. */
data class IconGrid(val columns: Int, val rows: Int) {
    init { require(columns in 1..10 && rows in 1..10) { "Grid columns and rows must be 1–10" } }
    val capacity: Int get() = columns * rows
}
internal object GridPolicy {
    fun columns(grid: IconGrid?, widthDp: Int): Int = grid?.columns ?: if (widthDp >= 600) 6 else 4
    fun pageCount(count: Int, grid: IconGrid?): Int = if (grid == null) 1 else maxOf(1, (count + grid.capacity - 1) / grid.capacity)
    fun page(page: Int, count: Int, grid: IconGrid?): Int = page.coerceIn(0, pageCount(count, grid) - 1)
    fun <T> items(items: List<T>, page: Int, grid: IconGrid?): List<T> =
        if (grid == null) items else items.drop(page(page, items.size, grid) * grid.capacity).take(grid.capacity)
}

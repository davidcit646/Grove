package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class AppCatalogTest {
    private data class Entry(val key: String, val packageName: String)

    private fun pipeline(
        entries: () -> List<Entry>,
        icon: (Entry) -> String,
    ) = CatalogPipeline(
        enumerate = entries,
        key = { it.key },
        packageName = { it.packageName },
        fallback = { "fallback" },
        loadIcon = icon,
    )

    @Test fun allIconsFailButAppsRemainAvailable() {
        val entries = listOf(Entry("a", "one"), Entry("b", "two"), Entry("c", "three"))
        var catalog = emptyList<Entry>()
        val icons = linkedMapOf<String, String>()
        var failures = -1
        var error: Exception? = null
        pipeline({ entries }) { throw IllegalStateException("icon") }.run(
            changedPackage = null,
            reusable = emptyMap(),
            current = { true },
            onCatalog = { loaded, _ -> catalog = loaded },
            onIcons = { icons.putAll(it) },
            onComplete = { failures = it },
            onFailure = { error = it },
        )
        assertEquals(entries, catalog)
        assertEquals(mapOf("a" to "fallback", "b" to "fallback", "c" to "fallback"), icons)
        assertEquals(3, failures)
        assertNull(error)
    }

    @Test fun oneChangedPackageReloadsOnlyThatIcon() {
        val entries = listOf(Entry("a", "one"), Entry("b", "two"), Entry("c", "three"))
        val requested = mutableListOf<String>()
        val published = linkedMapOf<String, String>()
        pipeline({ entries }) { entry -> requested += entry.key; "new-" + entry.key }.run(
            changedPackage = "two",
            reusable = mapOf("a" to "old-a", "b" to "old-b", "c" to "old-c"),
            current = { true },
            onCatalog = { _, _ -> },
            onIcons = { published.putAll(it) },
            onComplete = { },
            onFailure = { fail(it.message) },
        )
        assertEquals(listOf("b"), requested)
        assertEquals(mapOf("b" to "new-b"), published)
    }

    @Test fun canceledGenerationPublishesNoLateIconsOrCompletion() {
        var current = true
        var iconBatches = 0
        var completed = false
        pipeline({ listOf(Entry("a", "one")) }) { "icon" }.run(
            changedPackage = null,
            reusable = emptyMap(),
            current = { current },
            onCatalog = { _, _ -> current = false },
            onIcons = { iconBatches++ },
            onComplete = { completed = true },
            onFailure = { fail(it.message) },
        )
        assertEquals(0, iconBatches)
        assertFalse(completed)
    }

    @Test fun appListFailureIsDistinctFromIconFailure() {
        var catalogPublished = false
        var iconPublished = false
        var completed = false
        var error: Exception? = null
        pipeline({ throw IllegalStateException("catalog") }) { "icon" }.run(
            changedPackage = null,
            reusable = emptyMap(),
            current = { true },
            onCatalog = { _, _ -> catalogPublished = true },
            onIcons = { iconPublished = true },
            onComplete = { completed = true },
            onFailure = { error = it },
        )
        assertFalse(catalogPublished)
        assertFalse(iconPublished)
        assertFalse(completed)
        assertEquals("catalog", error?.message)
    }

    @Test fun iconSizeChangeInvalidatesCache() {
        val cache = SizedCache<String>()
        cache.useSize(48)
        cache.putAll(mapOf("a" to "icon"))
        assertEquals("icon", cache["a"])
        cache.useSize(48)
        assertEquals("icon", cache["a"])
        cache.useSize(64)
        assertNull(cache["a"])
    }
}

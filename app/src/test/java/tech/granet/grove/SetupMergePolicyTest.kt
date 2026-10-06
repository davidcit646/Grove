package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SetupMergePolicyTest {
    @Test fun setupPreservesNewGridThemeAndUneditedSearchChoices() {
        val base = Config()
        val current = base.copy(homeGrid = IconGrid(10, 10), themeMode = ThemeMode.DARK,
            search = SearchSettings(contactIndexing = true))
        val draft = base.copy(favorites = listOf("a/.B"))
        val merged = requireNotNull(SetupMergePolicy.merge(base, draft, current))
        assertEquals(current.homeGrid, merged.homeGrid); assertEquals(current.themeMode, merged.themeMode)
        assertEquals(current.search, merged.search); assertEquals(draft.favorites, merged.favorites)
    }
    @Test fun conflictingSearchChangesCannotOverwriteSettings() {
        val base = Config()
        val draft = base.copy(search = base.search.copy(contacts = true))
        val current = base.copy(search = base.search.copy(files = true))
        assertNull(SetupMergePolicy.merge(base, draft, current))
    }
}

package tech.granet.grove

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SearchFeaturesTest {
    @Test fun everyProviderCombinationSurvivesRoundTripAndKeepsProtectedChoices() {
        for (mask in 0 until 8) {
            val search = SearchSettings(contacts = true, fileIndexing = true,
                calculator = mask and 1 != 0, androidSettings = mask and 2 != 0, groveSettings = mask and 4 != 0)
            assertEquals(search, Config.parse(Config(search = search).json()).search)
        }
    }
    @Test fun olderExportsDefaultProvidersOnWithoutOptingIntoProtectedSources() {
        for (version in 7..11) {
            val root = JSONObject(Config().json()).put("version", version)
            if (version < 9) root.put("wallpaper", 0)
            root.getJSONObject("search").apply { remove("calculator"); remove("androidSettings"); remove("groveSettings") }
            val search = Config.parse(root.toString()).search
            assertTrue(search.calculator && search.androidSettings && search.groveSettings)
            assertFalse(search.contacts || search.files || search.contactIndexing || search.fileIndexing)
        }
    }
    @Test fun malformedProviderFieldsAreRejectedBeforePublication() {
        for (field in listOf("calculator", "androidSettings", "groveSettings")) {
            for (bad in listOf("true", 1, JSONObject.NULL)) {
                val root = JSONObject(Config().json())
                root.getJSONObject("search").put(field, bad)
                try { Config.parse(root.toString()); fail("Accepted $field of wrong type") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }
    @Test fun providerOnlyChangesNeverReconcileProtectedSources() {
        val before = SearchSettings(contacts = true, files = true, contactIndexing = true, fileIndexing = true)
        var calls = 0
        val changes = listOf(before.copy(calculator = false), before.copy(androidSettings = false),
            before.copy(groveSettings = false), before.copy(calculator = false, androidSettings = false, groveSettings = false))
        changes.forEach { after ->
            assertFalse(SearchSettingsEffects.protectedSources(before, after))
            assertTrue(SearchSettingsEffects.reconcile(before, after, { calls++; false }, { calls++; false }))
        }
        assertEquals(0, calls)
    }
    @Test fun protectedEffectsStayIndependentAndBothFailuresAreChecked() {
        val before = SearchSettings()
        val calls = mutableListOf<String>()
        assertTrue(SearchSettingsEffects.reconcile(before, before.copy(contacts = true),
            { calls.add("contacts"); true }, { calls.add("files"); true }))
        assertEquals(listOf("contacts"), calls)
        calls.clear()
        assertFalse(SearchSettingsEffects.reconcile(before, before.copy(contactIndexing = true, fileIndexing = true),
            { calls.add("contacts"); false }, { calls.add("files"); false }))
        assertEquals(listOf("contacts", "files"), calls)
    }
    @Test fun setupContactChoicesPreserveConcurrentProviderChoices() {
        val base = Config()
        val draft = base.copy(search = base.search.copy(contacts = true, files = true))
        val current = base.copy(search = base.search.copy(calculator = false, androidSettings = false, groveSettings = false))
        val merged = requireNotNull(SetupMergePolicy.merge(base, draft, current))
        assertEquals(current.search.copy(contacts = true, files = true), merged.search)
    }
    @Test fun failedProviderWriteKeepsSnapshotAndPublishesNothing() {
        var publications = 0
        val repository = SettingsRepository({ Config() }, { error("disk") }, {}, published = { publications++ })
        val before = repository.snapshot()
        assertTrue(repository.update { it.copy(search = it.search.copy(calculator = false, groveSettings = false)) } is SettingsOutcome.Unavailable)
        assertEquals(before, repository.snapshot()); assertEquals(0, publications)
    }
}

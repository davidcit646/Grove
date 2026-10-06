package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SettingsDiscoveryTest {
    private val grove = SettingsMatcher(SettingsCatalogue.grove)
    private val android = SettingsMatcher(SettingsCatalogue.android)
    @Test fun catalogueHasUniqueIdsAndEveryTypedToggleHasADestination() {
        val all = SettingsCatalogue.grove + SettingsCatalogue.android
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(SettingKey.entries.toSet(), SettingsCatalogue.grove.mapNotNull { it.key }.toSet())
        SettingsCatalogue.grove.forEach { entry ->
            val target = entry.destination as SettingsDestination.Grove
            assertTrue(target.route in SettingsPages.routes)
            assertEquals(target, SettingsCatalogue.destination(target.route, target.anchor))
        }
        assertNull(SettingsCatalogue.destination("search", "unknown"))
        assertNull(SettingsCatalogue.destination("review", "APPLY"))
        assertNull(SettingsCatalogue.destination("unknown", null))
    }
    @Test fun aliasesPunctuationCaseAccentsAndFuzzyTermsResolve() {
        assertEquals("android-wifi", android.matching("WÍ-FI").first().id)
        assertEquals("android-bluetooth", android.matching("bluetoth").first().id)
        assertTrue(android.matching("flight mode").any { it.id == "android-airplane" })
        assertEquals("widgets", grove.matching("widgets").first().id)
        assertEquals("theme", grove.matching("dark mode").first().id)
        assertTrue(grove.matching("grid").map { it.id }.containsAll(listOf("homeGrid", "drawerGrid")))
        assertTrue(grove.matching("clock").any { it.id == "CLOCK" })
        assertTrue(grove.matching("wifi").isEmpty())
    }
    @Test fun providerSwitchesHaveSearchableControlsWithoutHijackingDeviceQueries() {
        assertTrue(grove.matching("calculator").any { it.id == "CALCULATOR" })
        assertTrue(grove.matching("android settings search").any { it.id == "ANDROID_SETTINGS" })
        assertTrue(grove.matching("grove settings search").any { it.id == "GROVE_SETTINGS" })
        assertTrue(grove.matching("bluetooth").isEmpty())
        for (id in listOf("CALCULATOR", "ANDROID_SETTINGS", "GROVE_SETTINGS")) {
            assertEquals(SettingsDestination.Grove("search", id), SettingsCatalogue.entry(id)!!.destination)
        }
    }
    @Test fun emptyQueriesBoundsCategoriesAndCanonicalDeduplication() {
        assertTrue(grove.matching("").isEmpty())
        assertTrue(grove.matching("  ").isEmpty())
        assertTrue(grove.matching("settings", 0).isEmpty())
        assertTrue(grove.matching("settings").all { it.id.startsWith("category-") })
        assertTrue(grove.matching("settings").size <= 6)
        val widget = SettingsCatalogue.entry("widgets")!!
        val results = SettingsMatcher(listOf(widget, widget.copy(id = "duplicate"))).matching("widget")
        assertEquals(1, results.size)
        assertEquals("widgets", results.first().id)
    }
    @Test fun providerOrderAndWidgetFallbackRemainHonest() {
        val groveRows = grove.matching("widgets")
        val systemRows = SettingsSearchFallback.rows(android.matching("widgets"), SettingsCatalogue.android)
        val frame = SettingsMatches(groveRows, systemRows)
        assertEquals(groveRows, frame.ordered.take(groveRows.size))
        assertTrue(systemRows.any { it.id == "android-home" })
        assertEquals("android-search", systemRows.last().id)
        assertTrue(systemRows.none { it.title.contains("widget", ignoreCase = true) })
        val rootOnly = SettingsCatalogue.android.filter { it.id == "android-root" }
        assertEquals("android-root", SettingsSearchFallback.rows(emptyList(), rootOnly).single().id)
        assertTrue(SettingsSearchFallback.rows(emptyList(), emptyList()).isEmpty())
        assertEquals(1, SettingsSearchFallback.rows(rootOnly, rootOnly).size)
    }
    @Test fun frameChangesOnlyWhenSettingsRowsChangeAndRejectsObsoletePublication() {
        val target = Any(); val gate = SearchFrameGate()
        val rows = SettingsMatches(grove.matching("widgets"), emptyList())
        assertTrue(gate.shouldRender(target, rows))
        assertFalse(gate.shouldRender(target, rows.copy()))
        assertTrue(gate.shouldRender(target, rows.copy(android = android.matching("widgets"))))
        assertFalse(SearchPublicationGate.allowed(1, 2, true, true, true))
        assertFalse(SearchPublicationGate.allowed(2, 2, false, true, true))
    }
    @Test fun availabilityFailureClosesOnlyAffectedDestinations() {
        val available = SettingsCapabilities.snapshot(SettingsCatalogue.android) {
            when (it.id) {
                "android-wifi" -> true
                "android-bluetooth" -> throw SecurityException("Unavailable")
                else -> false
            }
        }
        assertEquals(listOf("android-wifi"), available.entries.map { it.id })
        assertEquals(SettingsCapabilities.Availability.READY, available.states["android-wifi"])
        assertEquals(SettingsCapabilities.Availability.FAILED, available.states["android-bluetooth"])
        assertEquals(SettingsCapabilities.Availability.UNAVAILABLE, available.states["android-airplane"])
        assertTrue(available.failed)
        val unsupported = SettingsCapabilities.snapshot(SettingsCatalogue.android) { false }
        assertTrue(unsupported.entries.isEmpty())
        assertFalse(unsupported.failed)
    }
    @Test fun systemHandlerMustBeEnabledExportedPermittedAndInSettingsPackage() {
        val valid = SettingsHandler("system.settings", true, true, true, true, true)
        val packages = setOf(valid.packageName)
        assertTrue(SettingsCapabilities.allowed(valid, packages))
        listOf(valid.copy(enabled = false), valid.copy(applicationEnabled = false),
            valid.copy(exported = false), valid.copy(system = false), valid.copy(permitted = false),
            valid.copy(packageName = "imposter")).forEach { assertFalse(SettingsCapabilities.allowed(it, packages)) }
        assertFalse(SettingsCapabilities.allowed(valid, emptySet()))
    }
    @Test fun launchRechecksMissingHandlersAndContainsPlatformFailures() {
        var opened = false
        assertEquals(SettingsCapabilities.Launch.UNAVAILABLE,
            SettingsCapabilities.launch<String>({ null }) { opened = true })
        assertFalse(opened)
        assertEquals(SettingsCapabilities.Launch.FAILED,
            SettingsCapabilities.launch<String>({ throw SecurityException() }) { opened = true })
        assertFalse(opened)
        assertEquals(SettingsCapabilities.Launch.FAILED,
            SettingsCapabilities.launch({ "component" }) { throw IllegalStateException() })
        assertEquals(SettingsCapabilities.Launch.OPENED,
            SettingsCapabilities.launch({ "component" }) { opened = true })
        assertTrue(opened)
    }
}

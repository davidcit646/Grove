package tech.granet.grove

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultsTest {
    @Test fun emptyQueryShowsNoResults() {
        assertEquals(emptyList<String>(), SearchResults.matching(listOf("Camera"), Search.prepare("  ")) { Search.normalize(it) })
    }

    @Test fun exactPrefixAndFuzzyResultsKeepStableOrder() {
        val labels = listOf("Google Maps", "Maps Go", "Maps", "Maps Lite", "Calendar")
        assertEquals(listOf("Maps", "Maps Go", "Maps Lite", "Google Maps"),
            SearchResults.matching(labels, Search.prepare("maps")) { Search.normalize(it) })
        assertEquals(listOf("Maps", "Maps Go"),
            SearchResults.matching(labels, Search.prepare("maps"), 2) { Search.normalize(it) })
    }

    @Test fun cachedSnapshotMatchesExistingRanking() {
        val labels = List(15_000) { index ->
            when (index % 4) {
                0 -> "Maps $index"
                1 -> "Maple $index"
                2 -> "Calendar $index"
                else -> "Camera $index"
            }
        }
        val cached = SearchResults.prepare(labels) { Search.normalize(it) }
        for (query in listOf("maps", "map", "mspa", "calendar")) {
            assertEquals(
                SearchResults.matching(labels, Search.prepare(query), 12) { Search.normalize(it) },
                SearchResults.matching(cached, Search.prepare(query), 12),
            )
        }
    }

    @Test fun boundedResultsKeepEarliestEqualScoreAtLargeScale() {
        val labels = List(20_000) { "file $it" }
        assertEquals(labels.take(12), SearchResults.matching(labels, Search.prepare("file"), 12) { it })
        assertEquals(emptyList<String>(), SearchResults.matching(labels, Search.prepare("file"), 0) { it })
    }
}

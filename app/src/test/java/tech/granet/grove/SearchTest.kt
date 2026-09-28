package tech.granet.grove
import org.junit.Assert.*
import org.junit.Test
class SearchTest {
    @Test fun accentsAndCaseMatch() { assertEquals(3, Search.score("Café", "CAFE")) }
    @Test fun allWordsAreRequired() { assertEquals(-1, Search.score("Google Maps", "google mail")) }
    @Test fun exactOutranksPrefixAndSubstring() {
        assertTrue(Search.score("Maps", "maps") > Search.score("Maps Go", "maps"))
        assertTrue(Search.score("Maps Go", "maps") > Search.score("Google Maps", "maps"))
    }
    @Test fun emptyQueryIncludesEveryApp() { assertEquals(0, Search.score("Camera", "  ")) }
    @Test fun multiWordOrderCanDiffer() { assertEquals(1, Search.score("Google Maps", "maps google")) }
    @Test fun preparedSearchMatchesExistingRanking() {
        val labels = listOf("Café", "Google Maps", "Maps Go", "Camera", "Email")
        for (query in listOf("", "  ", "CAFE", "maps", "maps google", "missing")) {
            val prepared = Search.prepare(query)
            for (label in labels) assertEquals(Search.score(label, query), Search.scoreNormalized(Search.normalize(label), prepared))
        }
    }
    @Test fun preparedQueryPreservesAccentAndMultiwordMatching() {
        assertEquals(1, Search.scoreNormalized(Search.normalize("Café Maps"), Search.prepare("maps CAFE")))
    }
    @Test fun typosMatchButUnrelatedWordsDoNot() {
        assertTrue(Search.score("Calculator", "calculatr") >= 0)
        assertTrue(Search.score("Documents", "documnts") >= 0)
        assertEquals(-1, Search.score("Calculator", "calendar"))
    }
}

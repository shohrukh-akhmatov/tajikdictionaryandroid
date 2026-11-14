package tj.tajikdictionary.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.tajikdictionary.domain.model.Language

class WordStemmerTest {
    @Test
    fun extractsTajikPluralAndVerbStems() {
        val pluralStems = WordStemmer.candidateStems("китобхо", setOf(Language.TAJIK))
        assertTrue("Expected 'китоб' in $pluralStems", "китоб" in pluralStems)

        val verbStems = WordStemmer.candidateStems("меравам", setOf(Language.TAJIK))
        assertTrue("Expected 'рафтан' in $verbStems", "рафтан" in verbStems)

        val possessiveStems = WordStemmer.candidateStems("дустам", setOf(Language.TAJIK))
        assertTrue("Expected 'дуст' in $possessiveStems", "дуст" in possessiveStems)
    }

    @Test
    fun computesBoundedEditDistance() {
        assertEquals(0, WordStemmer.editDistance("салом", "салом"))
        assertEquals(1, WordStemmer.editDistance("салам", "салом"))
        assertEquals(3, WordStemmer.editDistance("китоб", "хона", maxDistance = 2))
    }
}

package tj.tajikdictionary.core.text

import org.junit.Assert.assertEquals
import org.junit.Test
import tj.tajikdictionary.domain.model.Language

class LanguageDetectorTest {
    @Test
    fun detectsCandidateLanguagesPerSpec() {
        assertEquals(setOf(Language.ENGLISH), LanguageDetector.candidates("hello"))
        assertEquals(setOf(Language.TAJIK), LanguageDetector.candidates("ҳафта"))
        assertEquals(setOf(Language.RUSSIAN), LanguageDetector.candidates("цветок"))
        assertEquals(setOf(Language.TAJIK, Language.RUSSIAN), LanguageDetector.candidates("дар"))
        assertEquals(setOf(Language.TAJIK, Language.RUSSIAN, Language.ENGLISH), LanguageDetector.candidates("123"))
    }
}

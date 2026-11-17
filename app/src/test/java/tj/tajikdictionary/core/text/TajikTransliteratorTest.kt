package tj.tajikdictionary.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TajikTransliteratorTest {
    @Test
    fun transliteratesCommonTajikWordsAndSentences() {
        assertEquals("salom", TajikTransliterator.toLatin("Салом"))
        assertEquals("sohibī", TajikTransliterator.toLatin("соҳибӣ"))
        assertEquals("muvaffaqiyat", TajikTransliterator.toLatin("муваффақият"))
        assertEquals("khūrdan", TajikTransliterator.toLatin("хӯрдан"))
        assertEquals("javob", TajikTransliterator.toLatin("ҷавоб"))
        assertEquals("ma’no", TajikTransliterator.toLatin("маъно"))
        assertEquals("mekunam", TajikTransliterator.toLatin("мекунам"))
        assertEquals("yek", TajikTransliterator.toLatin("ек"))
    }
}

package tj.tajikdictionary.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun matchesSharedNormalizationVectors() {
        val vectors = listOf(
            "Ҳафта" to "хафта",
            "хӯрдан" to "хурдан",
            "ҶАВОБ" to "чавоб",
            "ғамгин" to "гамгин",
            "қалам" to "калам",
            "бинӣ" to "бини",
            "ёрӣ" to "ери",
            "Чёрный" to "черный",
            "мой" to "мой",
            "  кор   кардан " to "кор кардан",
            "кор-бор" to "кор бор",
            "Don't" to "dont",
            "café" to "cafe",
            "To Go" to "go",
        )
        vectors.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, TextNormalizer.searchKey(input))
        }
    }
}

package tj.tajikdictionary.core.text

import java.text.Normalizer

/**
 * Search normalization shared by all platforms.
 *
 * MUST stay identical to `tools/build_dictionary.py::normalize` (which builds the `search_key`
 * column) and to the iOS `TextNormalizer.swift`. See docs/DATA_AND_SEARCH.md §3.
 */
object TextNormalizer {
    private const val BREVE = '\u0306' // keeps "й" distinct from "и"
    private val fold = mapOf('ғ' to 'г', 'қ' to 'к', 'ҳ' to 'х', 'ҷ' to 'ч')
    private val apostrophes = Regex("['\u2018\u2019\u02BC`]")
    private val nonWord = Regex("[^\\p{L}\\p{N} ]+")
    private val spaces = Regex("\\s+")

    /** "Ҳафта" → "хафта", "хӯрдан" → "хурдан", "Don't" → "dont". */
    fun normalize(text: String): String {
        val lower = text.lowercase()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = buildString(decomposed.length) {
            for (ch in decomposed) {
                val isMark = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
                if (!isMark || ch == BREVE) append(fold[ch] ?: ch)
            }
        }
        val composed = Normalizer.normalize(stripped, Normalizer.Form.NFC)
        return composed
            .replace(apostrophes, "")
            .replace('_', ' ')
            .replace(nonWord, " ")
            .replace(spaces, " ")
            .trim()
    }

    /** [normalize] + drops the English infinitive marker: "to go" → "go". */
    fun searchKey(text: String): String {
        val s = normalize(text)
        return if (s.startsWith("to ") && s.length > 3) s.substring(3) else s
    }
}

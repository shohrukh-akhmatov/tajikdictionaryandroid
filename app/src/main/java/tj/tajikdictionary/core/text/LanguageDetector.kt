package tj.tajikdictionary.core.text

import tj.tajikdictionary.domain.model.Language

/**
 * Guesses which dictionary languages a raw query can belong to (docs/DATA_AND_SEARCH.md §4.1).
 * Rules are evaluated in order; the first match wins.
 */
object LanguageDetector {
    private val tajikOnly = setOf('ғ', 'ӣ', 'қ', 'ӯ', 'ҳ', 'ҷ')
    private val russianOnly = setOf('ц', 'щ', 'ы', 'ь')

    fun candidates(rawQuery: String): Set<Language> {
        val s = rawQuery.lowercase()
        return when {
            s.any { it in 'a'..'z' } -> setOf(Language.ENGLISH)
            s.any { it in tajikOnly } -> setOf(Language.TAJIK)
            s.any { it in russianOnly } -> setOf(Language.RUSSIAN)
            s.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC } ->
                setOf(Language.TAJIK, Language.RUSSIAN)
            else -> Language.entries.toSet()
        }
    }
}

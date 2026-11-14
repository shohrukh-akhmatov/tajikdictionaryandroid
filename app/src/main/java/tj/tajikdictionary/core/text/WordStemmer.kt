package tj.tajikdictionary.core.text

import tj.tajikdictionary.domain.model.Language
import kotlin.math.min

/**
 * Rule-based stemmer and bounded edit-distance helper for offline fallback search.
 * Operates on normalized `searchKey` strings produced by [TextNormalizer].
 */
object WordStemmer {
    private val tajikPrefixes = listOf("ме", "на", "би", "бе", "бо", "хам")
    private val tajikSuffixes = listOf(
        "амон", "атон", "ашон", "хо", "он", "ро", "ам", "ат", "аш",
        "ем", "ед", "анд", "и", "е",
    )
    private val tajikPresentToInfinitive = mapOf(
        "рав" to "рафтан",
        "о" to "омадан",
        "оя" to "омадан",
        "бин" to "дидан",
        "гу" to "гуфтан",
        "гуй" to "гуфтан",
        "кун" to "кардан",
        "бош" to "будан",
        "дор" to "доштан",
        "навис" to "навиштан",
        "омуз" to "омухтан",
        "фуруш" to "фурухтан",
        "дех" to "додан",
        "гир" to "гирифтан",
        "шин" to "нишастан",
        "хез" to "хестан",
    )

    private val russianSuffixes = listOf(
        "ами", "ями", "ов", "ев", "ах", "ях", "ом", "ем", "ой", "ей", "ы", "и", "а", "у", "е",
    )

    /** Returns candidate base search keys in priority order (excluding [key] itself). */
    fun candidateStems(key: String, languages: Set<Language>): List<String> {
        if (key.length < 3) return emptyList()
        val stems = LinkedHashSet<String>()

        if (Language.TAJIK in languages) {
            val bases = LinkedHashSet<String>()
            bases += key
            for (prefix in tajikPrefixes) {
                if (key.startsWith(prefix) && key.length - prefix.length >= 2) {
                    val afterPrefix = key.removePrefix(prefix)
                    bases += afterPrefix
                    stems += afterPrefix
                }
            }
            for (base in bases.toList()) {
                for (suffix in tajikSuffixes) {
                    if (base.endsWith(suffix) && base.length - suffix.length >= 2) {
                        val stripped = base.removeSuffix(suffix)
                        stems += stripped
                        tajikPresentToInfinitive[stripped]?.let { stems += it }
                    }
                }
                tajikPresentToInfinitive[base]?.let { stems += it }
            }
        }

        if (Language.RUSSIAN in languages && key.length >= 4) {
            for (suffix in russianSuffixes) {
                if (key.endsWith(suffix) && key.length - suffix.length >= 3) {
                    stems += key.removeSuffix(suffix)
                }
            }
        }

        if (Language.ENGLISH in languages && key.length >= 4) {
            when {
                key.endsWith("ies") && key.length > 4 -> stems += key.dropLast(3) + "y"
                key.endsWith("ing") && key.length > 5 -> {
                    val base = key.dropLast(3)
                    stems += base
                    stems += "${base}e"
                }
                key.endsWith("ed") && key.length > 4 -> {
                    val base = key.dropLast(2)
                    stems += base
                    stems += "${base}e"
                }
                key.endsWith("es") && key.length > 4 -> stems += key.dropLast(2)
                key.endsWith("s") && key.length > 3 -> stems += key.dropLast(1)
            }
        }

        stems.remove(key)
        return stems.toList()
    }

    /** Levenshtein edit distance with early exit above [maxDistance]. */
    fun editDistance(a: String, b: String, maxDistance: Int = 2): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}

package tj.tajikdictionary.core.text

/**
 * Deterministic Cyrillic-to-Latin pronunciation guide for Tajik words and example sentences.
 * Gives learners an offline phonetic reading aid even when the device lacks a Tajik TTS voice.
 */
object TajikTransliterator {
    private val VOWEL_OR_BOUNDARY = setOf(
        'а', 'е', 'ё', 'и', 'ӣ', 'о', 'у', 'ӯ', 'э', 'ю', 'я', 'ъ', 'ь',
    )

    fun toLatin(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()
        val sb = StringBuilder(lower.length + 8)
        for (i in lower.indices) {
            val ch = lower[i]
            val prev = if (i > 0) lower[i - 1] else ' '
            val atWordStart = i == 0 || !prev.isLetter()
            when (ch) {
                'а' -> sb.append('a')
                'б' -> sb.append('b')
                'в' -> sb.append('v')
                'г' -> sb.append('g')
                'ғ' -> sb.append("gh")
                'д' -> sb.append('d')
                'е' -> sb.append(if (atWordStart || prev in VOWEL_OR_BOUNDARY) "ye" else "e")
                'ё' -> sb.append("yo")
                'ж' -> sb.append("zh")
                'з' -> sb.append('z')
                'и' -> sb.append('i')
                'ӣ' -> sb.append('ī')
                'й' -> sb.append('y')
                'к' -> sb.append('k')
                'қ' -> sb.append('q')
                'л' -> sb.append('l')
                'м' -> sb.append('m')
                'н' -> sb.append('n')
                'о' -> sb.append('o')
                'п' -> sb.append('p')
                'р' -> sb.append('r')
                'с' -> sb.append('s')
                'т' -> sb.append('t')
                'у' -> sb.append('u')
                'ӯ' -> sb.append('ū')
                'ф' -> sb.append('f')
                'х' -> sb.append("kh")
                'ҳ' -> sb.append('h')
                'ч' -> sb.append("ch")
                'ҷ' -> sb.append('j')
                'ш' -> sb.append("sh")
                'ъ' -> sb.append('’')
                'ы' -> sb.append('i')
                'ь' -> Unit
                'э' -> sb.append('e')
                'ю' -> sb.append("yu")
                'я' -> sb.append("ya")
                'ц' -> sb.append("ts")
                'щ' -> sb.append("shch")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

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

    private val latinDigraphs = mapOf(
        "sh" to "ш", "ch" to "ч", "kh" to "х", "gh" to "ғ", "zh" to "ж",
        "yo" to "ё", "yu" to "ю", "ya" to "я", "ye" to "е",
    )
    private val latinSingles = mapOf(
        'a' to "а", 'b' to "б", 'v' to "в", 'w' to "в", 'g' to "г", 'd' to "д", 'e' to "е",
        'z' to "з", 'i' to "и", 'ī' to "ӣ", 'y' to "й", 'k' to "к", 'q' to "қ", 'l' to "л",
        'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'r' to "р", 's' to "с", 't' to "т",
        'u' to "у", 'ū' to "ӯ", 'f' to "ф", 'x' to "х", 'h' to "ҳ", 'j' to "ҷ", 'c' to "ч",
        'ǧ' to "ғ", 'ğ' to "ғ", 'š' to "ш", 'č' to "ч", 'ž' to "ж", 'ç' to "ч",
        '\'' to "ъ", '’' to "ъ", 'ʼ' to "ъ",
    )

    /**
     * Reads Latin-script Tajik ("salom", "khona", "shahr", "dūst") as Cyrillic so learners without a
     * Tajik keyboard can still search. Inverse of [toLatin], plus common informal spellings
     * (x = х, h = ҳ, j = ҷ, q = қ). A word-initial "e" becomes "э" (эй, эълон).
     */
    fun fromLatin(text: String): String {
        val lower = text.trim().lowercase()
        val sb = StringBuilder(lower.length)
        var i = 0
        while (i < lower.length) {
            val ch = lower[i]
            val atWordStart = i == 0 || !lower[i - 1].isLetter()
            val digraph = if (i + 1 < lower.length) latinDigraphs[lower.substring(i, i + 2)] else null
            if (digraph != null) {
                sb.append(digraph)
                i += 2
            } else {
                sb.append(if (ch == 'e' && atWordStart) "э" else latinSingles[ch] ?: ch.toString())
                i += 1
            }
        }
        return sb.toString()
    }

    /** True when [text] contains Latin letters that [fromLatin] can read as Tajik. */
    fun looksLatin(text: String): Boolean = text.any { it in 'a'..'z' || it in 'A'..'Z' }
}

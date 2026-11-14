package tj.tajikdictionary.domain.model

/** A dictionary language. [code] matches `entries.lang` in the bundled database. */
enum class Language(val code: String, val tag: String) {
    TAJIK("tg", "TJ"),
    RUSSIAN("ru", "RU"),
    ENGLISH("en", "EN");

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: error("Unknown language code: $code")
    }
}

/** The two dictionary modes. Each searches Tajik + one partner language at the same time. */
enum class DictionaryMode(val partner: Language, val title: String) {
    TJ_RU(Language.RUSSIAN, "TJK ⇄ RUS"),
    TJ_EN(Language.ENGLISH, "TJK ⇄ ENG");

    val languages: Set<Language> get() = setOf(Language.TAJIK, partner)

    /** Languages to show translations in, for an entry of [language], in this mode. */
    fun translationTarget(language: Language): Language =
        if (language == Language.TAJIK) partner else Language.TAJIK
}

/** Part-of-speech codes used in the database. */
enum class PartOfSpeech(val code: String, val label: String) {
    NOUN("n", "noun"), VERB("v", "verb"), ADJECTIVE("adj", "adj."), ADVERB("adv", "adv."),
    PRONOUN("pron", "pron."), NUMERAL("num", "num."), PREPOSITION("prep", "prep."),
    CONJUNCTION("conj", "conj."), INTERJECTION("intj", "interj."), PARTICLE("part", "part."),
    PHRASE("phr", "phrase");

    companion object {
        fun fromCode(code: String?): PartOfSpeech? = entries.firstOrNull { it.code == code }
    }
}

package tj.tajikdictionary.domain.model

/** One row in the search result list. */
data class SearchResult(
    val id: Long,
    val language: Language,
    val headword: String,
    val pos: PartOfSpeech?,
    /** Up to 4 translations into the mode's other language, comma-separated. */
    val preview: String,
)

/**
 * TRANSLIT = Latin-script input read as Tajik Cyrillic ("salom" → "салом").
 * WORDS = a multi-word phrase that isn't an entry, shown word by word ("добрый день" → добрый, день).
 */
enum class SearchMatchType { DIRECT, STEM, FUZZY, TRANSLIT, WORDS }

/** Result of a search, including whether we suggest switching the mode or used fallback matching. */
data class SearchOutcome(
    val query: String,
    val results: List<SearchResult>,
    val searchedLanguages: Set<Language>,
    /** Non-null when the query looks like a language that is not part of the current mode. */
    val suggestedMode: DictionaryMode?,
    val matchType: SearchMatchType = SearchMatchType.DIRECT,
    /** Cyrillic reading of a Latin-script query when Tajik results were added through it. */
    val transliteratedQuery: String? = null,
) {
    companion object {
        val Empty = SearchOutcome("", emptyList(), emptySet(), null)
    }
}

data class TopicCount(val topic: String, val count: Int)

data class Entry(
    val id: Long,
    val language: Language,
    val headword: String,
    val pos: PartOfSpeech?,
    val frequency: Int,
    val topic: String?,
)

data class Translation(
    val entryId: Long,
    val language: Language,
    val headword: String,
    val pos: PartOfSpeech?,
    val note: String?,
    val position: Int = 0,
)

data class Example(val tg: String, val ru: String?, val en: String?)

data class EntryDetail(
    val entry: Entry,
    /** Translations grouped by language, in sense order. */
    val translations: Map<Language, List<Translation>>,
    val examples: List<Example>,
    /** Same-language entries sharing the same headword/search_key with a different part of speech (e.g. соҳибӣ n. vs adj.). */
    val homographs: List<SearchResult> = emptyList(),
    /** Same-language entries that share translations with this entry. */
    val synonyms: List<SearchResult> = emptyList(),
    /** Same-language entries sharing the word root/prefix. */
    val relatedWords: List<SearchResult> = emptyList(),
)

data class DictionaryInfo(val dataVersion: String, val stats: String)

enum class ProfileKind(val code: String, val defaultDailyGoal: Int, val roundSize: Int) {
    ADULT("adult", defaultDailyGoal = 10, roundSize = 10),
    CHILD("child", defaultDailyGoal = 5, roundSize = 6);

    companion object {
        fun fromCode(raw: String?): ProfileKind =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: ADULT
    }
}

/**
 * Local learner profile on the device. Each profile has an isolated learning database and learning settings.
 * Limited to [MAX_PROFILES] (3) per device so local storage and Google/iCloud backups remain compact.
 */
data class LearnerProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val colorHex: String,
    val kind: ProfileKind = ProfileKind.ADULT,
    val createdAtMs: Long = 0L,
) {
    val isChild: Boolean get() = kind == ProfileKind.CHILD

    companion object {
        const val MAX_PROFILES = 3
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_NAME = "Me"
        const val DEFAULT_AVATAR = "🦊"
        const val DEFAULT_COLOR = "#087653"
        const val WHO_IS_LEARNING_COOLDOWN_MS = 10 * 60 * 1000L

        val AVATARS = listOf("🦊", "🦁", "🐼", "🦉", "🐯", "🦋", "🚀", "🌟", "📚", "🏔️", "🎨", "⚽")
        val COLORS = listOf("#087653", "#1E6F9F", "#7B1FA2", "#E65100", "#C62828", "#2E7D32")

        fun defaultProfile(name: String = DEFAULT_NAME, createdAtMs: Long = 0L) = LearnerProfile(
            id = DEFAULT_PROFILE_ID,
            name = name,
            avatar = DEFAULT_AVATAR,
            colorHex = DEFAULT_COLOR,
            kind = ProfileKind.ADULT,
            createdAtMs = createdAtMs,
        )
    }
}

/** Lightweight summary of a profile's learning progress for the Family Board & Profile Switcher. */
data class ProfileStatsSnapshot(
    val profileId: String,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalXp: Int = 0,
    val masteredCount: Int = 0,
    val knownWordsCount: Int = 0,
    val todayReviewed: Int = 0,
    val dailyGoal: Int = 10,
    val cefrCode: String = "A0",
    val lastActiveDateKey: String? = null,
) {
    fun practicedToday(todayDateKey: String): Boolean =
        lastActiveDateKey == todayDateKey && todayReviewed > 0
}

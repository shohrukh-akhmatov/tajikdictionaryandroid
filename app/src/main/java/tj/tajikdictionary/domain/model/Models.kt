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

enum class SearchMatchType { DIRECT, STEM, FUZZY }

/** Result of a search, including whether we suggest switching the mode or used fallback matching. */
data class SearchOutcome(
    val query: String,
    val results: List<SearchResult>,
    val searchedLanguages: Set<Language>,
    /** Non-null when the query looks like a language that is not part of the current mode. */
    val suggestedMode: DictionaryMode?,
    val matchType: SearchMatchType = SearchMatchType.DIRECT,
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
)

data class Example(val tg: String, val ru: String?, val en: String?)

data class EntryDetail(
    val entry: Entry,
    /** Translations grouped by language, in sense order. */
    val translations: Map<Language, List<Translation>>,
    val examples: List<Example>,
)

data class DictionaryInfo(val dataVersion: String, val stats: String)

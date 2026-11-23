package tj.tajikdictionary.data.dictionary

import android.database.Cursor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.tajikdictionary.core.text.LanguageDetector
import tj.tajikdictionary.core.text.TajikTransliterator
import tj.tajikdictionary.core.text.TextNormalizer
import tj.tajikdictionary.core.text.WordStemmer
import tj.tajikdictionary.domain.model.DictionaryInfo
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Entry
import tj.tajikdictionary.domain.model.EntryDetail
import tj.tajikdictionary.domain.model.Example
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.LexiconStats
import tj.tajikdictionary.domain.model.PartOfSpeech
import tj.tajikdictionary.domain.model.SearchMatchType
import tj.tajikdictionary.domain.model.SearchOutcome
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.domain.model.TopicCount
import tj.tajikdictionary.domain.model.Translation
import tj.tajikdictionary.domain.model.WordCardCandidate
import java.time.LocalDate

/** Read-only access to the bundled dictionary. SQL follows docs/DATA_AND_SEARCH.md §4. */
class DictionaryRepository(
    private val db: DictionaryDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun search(rawQuery: String, mode: DictionaryMode, limit: Int = 50): SearchOutcome =
        withContext(io) {
            val key = TextNormalizer.searchKey(rawQuery)
            if (key.isEmpty()) return@withContext SearchOutcome.Empty

            val candidates = LanguageDetector.candidates(rawQuery)
            val languages = candidates intersect mode.languages
            val primary = if (languages.isNotEmpty()) searchIn(key, rawQuery, languages, mode, limit) else null

            // Latin-script Tajik ("salom", "kitob", "rahmat"): learners often lack a Tajik keyboard.
            val cyrillic = if (TajikTransliterator.looksLatin(rawQuery)) TajikTransliterator.fromLatin(rawQuery) else ""
            val translitKey = TextNormalizer.searchKey(cyrillic)
            if (translitKey.isNotEmpty() && translitKey.none { it in 'a'..'z' }) {
                val tajik = searchIn(translitKey, rawQuery, setOf(Language.TAJIK), mode, limit)
                val tajikIsStrong = tajik.matchType != SearchMatchType.FUZZY && tajik.matchType != SearchMatchType.WORDS
                if (tajik.results.isNotEmpty() && (primary == null || primary.results.isEmpty())) {
                    return@withContext tajik.copy(
                        matchType = if (tajikIsStrong) SearchMatchType.TRANSLIT else tajik.matchType,
                        transliteratedQuery = cyrillic,
                    )
                }
                if (tajik.results.isNotEmpty() && primary != null && primary.matchType == SearchMatchType.WORDS) {
                    // Word-by-word is the weakest fallback: only a direct/stem Tajik reading beats it, and
                    // the two lists are never mixed ("good morning" must not add Cyrillic "гоод…").
                    return@withContext if (tajikIsStrong) {
                        tajik.copy(matchType = SearchMatchType.TRANSLIT, transliteratedQuery = cyrillic)
                    } else {
                        primary
                    }
                }
                if (tajik.results.isNotEmpty() && primary != null) {
                    if (tajikIsStrong) {
                        // Ambiguous input like "man" (EN man / TJ ман): exact hits of both first.
                        val primaryExact = primary.results.filter { TextNormalizer.searchKey(it.headword) == key }
                        val tajikExact = tajik.results.filter { TextNormalizer.searchKey(it.headword) == translitKey }
                        val merged = (primaryExact + tajikExact + primary.results + tajik.results)
                            .distinctBy { it.id }
                            .take(limit)
                        return@withContext primary.copy(
                            results = merged,
                            searchedLanguages = primary.searchedLanguages + Language.TAJIK,
                            transliteratedQuery = cyrillic,
                        )
                    }
                }
            }

            if (primary == null) {
                val suggestion = DictionaryMode.entries.firstOrNull { (candidates intersect it.languages).isNotEmpty() }
                return@withContext SearchOutcome(rawQuery, emptyList(), emptySet(), suggestion)
            }
            primary
        }

    /** Direct prefix → stem → typo fallback pipeline for one normalized [key] within [languages]. */
    private fun searchIn(
        key: String,
        rawQuery: String,
        languages: Set<Language>,
        mode: DictionaryMode,
        limit: Int,
    ): SearchOutcome = run {
            val direct = prefixSearch(key, languages, mode, limit)
            val hasExactDirect = direct.any { TextNormalizer.searchKey(it.headword) == key }
            if (direct.isNotEmpty() && hasExactDirect) {
                return@run SearchOutcome(rawQuery, direct, languages, null, SearchMatchType.DIRECT)
            }

            // Phase 1 fallback 1: Tajik / Russian / English morphological stem hints (exact lemma match)
            // Also checked when `direct` only had longer prefix hits (e.g. "китобҳо" vs "китобхона").
            val stems = WordStemmer.candidateStems(key, languages)
                .filter { if (direct.isNotEmpty()) it.length >= 3 else true }
            if (stems.isNotEmpty()) {
                val stemResults = exactMatches(stems, languages, mode, limit)
                if (stemResults.isNotEmpty()) {
                    val stemIds = stemResults.map { it.id }.toHashSet()
                    val combined = (stemResults + direct.filter { it.id !in stemIds }).take(limit)
                    return@run SearchOutcome(
                        rawQuery, combined, languages, null, SearchMatchType.STEM,
                    )
                }
            }
            if (direct.isNotEmpty()) {
                // If every prefix hit is a much longer word (4+ extra chars) and a 1-edit typo match exists
                // (e.g. "салам" vs prefix "саламандра" when user meant "салом"), surface the close typo match.
                val onlyLongPrefixHits = key.length >= 5 && direct.all {
                    TextNormalizer.searchKey(it.headword).length >= key.length + 4
                }
                if (onlyLongPrefixHits) {
                    val closeFuzzy = fuzzySearch(key, languages, mode, limit = 20, maxDistanceOverride = 1)
                    if (closeFuzzy.isNotEmpty()) {
                        val fuzzyIds = closeFuzzy.map { it.id }.toHashSet()
                        val combined = (closeFuzzy + direct.filter { it.id !in fuzzyIds }).take(limit)
                        return@run SearchOutcome(rawQuery, combined, languages, null, SearchMatchType.FUZZY)
                    }
                }
                return@run SearchOutcome(rawQuery, direct, languages, null, SearchMatchType.DIRECT)
            }

            // Phase 1 fallback 2: bounded edit-distance typo tolerance
            if (key.length >= 3) {
                val fuzzy = fuzzySearch(key, languages, mode, limit = 20)
                if (fuzzy.isNotEmpty()) {
                    return@run SearchOutcome(rawQuery, fuzzy, languages, null, SearchMatchType.FUZZY)
                }
            }

            // Fallback 3: a phrase that isn't an entry ("добрый день", "good morning") → look up each word.
            val wordResults = wordByWordSearch(key, languages, mode, limit)
            if (wordResults.isNotEmpty()) {
                return@run SearchOutcome(rawQuery, wordResults, languages, null, SearchMatchType.WORDS)
            }

            SearchOutcome(rawQuery, emptyList(), languages, suggestedMode = null)
        }

    /**
     * Looks up each word of a multi-word [key] separately (exact form, then base form, then prefix),
     * keeping query word order so a phrase reads left to right. Mirrors iOS `wordByWordSearch`.
     */
    private fun wordByWordSearch(
        key: String,
        languages: Set<Language>,
        mode: DictionaryMode,
        limit: Int,
    ): List<SearchResult> {
        return wordByWord(
            key, languages, limit,
            exact = { keys, n -> exactMatches(keys, languages, mode, n) },
            prefix = { word, n -> prefixSearch(word, languages, mode, n) },
        )
    }

    private fun exactMatches(
        keys: List<String>,
        languages: Set<Language>,
        mode: DictionaryMode,
        limit: Int,
    ): List<SearchResult> {
        val sql = exactMatchesSql(keys.size, languages.size, limit)
        val args = exactMatchesArgs(keys, languages, mode)

        return db.get().rawQuery(sql, args).use { c ->
            c.mapRows {
                SearchResult(
                    id = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    preview = stringOrNull(4).orEmpty(),
                )
            }
        }
    }

    private fun prefixSearch(
        key: String,
        languages: Set<Language>,
        mode: DictionaryMode,
        limit: Int,
    ): List<SearchResult> {
        val sql = prefixSearchSql(languages.size, limit)
        val args = prefixSearchArgs(key, languages, mode)

        return db.get().rawQuery(sql, args).use { c ->
            c.mapRows {
                SearchResult(
                    id = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    preview = stringOrNull(4).orEmpty(),
                )
            }
        }
    }

    private fun fuzzySearch(
        key: String,
        languages: Set<Language>,
        mode: DictionaryMode,
        limit: Int,
        maxDistanceOverride: Int? = null,
    ): List<SearchResult> {
        val maxDist = maxDistanceOverride ?: if (key.length >= 5) 2 else 1
        val minLen = (key.length - maxDist).coerceAtLeast(2)
        val maxLen = key.length + maxDist
        val placeholders = languages.joinToString(",") { "?" }
        val sql = """
            SELECT e.id, e.lang, e.headword, e.pos, e.search_key, e.frequency,
                   ${previewColumn("e", 4)} AS preview
            FROM entries e
            WHERE e.lang IN ($placeholders)
              AND length(e.search_key) BETWEEN ? AND ?
            ORDER BY e.frequency DESC
            LIMIT 1500
        """.trimIndent()
        val args = buildList {
            add(mode.partner.code)
            languages.forEach { add(it.code) }
            add(minLen.toString())
            add(maxLen.toString())
        }.toTypedArray()

        data class Scored(val dist: Int, val freq: Int, val result: SearchResult)
        val scored = ArrayList<Scored>()
        db.get().rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val candidateKey = c.getString(4)
                val dist = WordStemmer.editDistance(key, candidateKey, maxDist)
                if (dist <= maxDist) {
                    scored += Scored(
                        dist = dist,
                        freq = c.getInt(5),
                        result = SearchResult(
                            id = c.getLong(0),
                            language = Language.fromCode(c.getString(1)),
                            headword = c.getString(2),
                            pos = PartOfSpeech.fromCode(c.stringOrNull(3)),
                            preview = c.stringOrNull(6).orEmpty(),
                        ),
                    )
                }
            }
        }
        return scored.sortedWith(compareBy<Scored> { it.dist }.thenByDescending { it.freq })
            .take(limit)
            .map { it.result }
    }

    suspend fun topics(): List<TopicCount> = withContext(io) {
        db.get().rawQuery(
            """
            SELECT topic, COUNT(*) AS cnt FROM entries
            WHERE lang = 'tg' AND topic IS NOT NULL AND topic != ''
            GROUP BY topic ORDER BY cnt DESC, topic ASC
            """.trimIndent(),
            null,
        ).use { c -> c.mapRows { TopicCount(getString(0), getInt(1)) } }
    }

    suspend fun entriesByTopic(topic: String, mode: DictionaryMode): List<SearchResult> = withContext(io) {
        db.get().rawQuery(
            """
            SELECT e.id, e.lang, e.headword, e.pos,
                   ${previewColumn("e", 4, fixedPartner = true)} AS preview
            FROM entries e
            WHERE e.lang = 'tg' AND e.topic = ?
            ORDER BY e.frequency DESC, e.headword ASC
            """.trimIndent(),
            arrayOf(mode.partner.code, topic),
        ).use { c ->
            c.mapRows {
                SearchResult(
                    id = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    preview = stringOrNull(4).orEmpty(),
                )
            }
        }
    }

    suspend fun entry(id: Long): Entry? = withContext(io) {
        db.get().rawQuery(
            "SELECT id, lang, headword, pos, frequency, topic FROM entries WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c -> c.mapRows { toEntry() }.firstOrNull() }
    }

    suspend fun detail(id: Long, mode: DictionaryMode = DictionaryMode.TJ_RU): EntryDetail? = withContext(io) {
        val entry = entry(id) ?: return@withContext null
        val translations = db.get().rawQuery(
            """
            SELECT t2.id, t2.lang, t2.headword, t2.pos, t.note, t.position
            FROM translations t JOIN entries t2 ON t2.id = t.to_id
            WHERE t.from_id = ? ORDER BY t.position, t2.lang
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { c ->
            c.mapRows {
                Translation(
                    entryId = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    note = stringOrNull(4),
                    position = getInt(5),
                )
            }
        }
        var examples = db.get().rawQuery(
            "SELECT tg, ru, en FROM examples WHERE entry_id = ?",
            arrayOf(id.toString()),
        ).use { c -> c.mapRows { Example(getString(0), stringOrNull(1), stringOrNull(2)) } }

        // Reverse-direction entries (RU/EN) link examples via their primary Tajik translation.
        if (examples.isEmpty() && entry.language != Language.TAJIK) {
            val primaryTgId = translations.firstOrNull { it.language == Language.TAJIK }?.entryId
            if (primaryTgId != null) {
                examples = db.get().rawQuery(
                    "SELECT tg, ru, en FROM examples WHERE entry_id = ? LIMIT 3",
                    arrayOf(primaryTgId.toString()),
                ).use { c -> c.mapRows { Example(getString(0), stringOrNull(1), stringOrNull(2)) } }
            }
        }

        val entryKey = TextNormalizer.searchKey(entry.headword)
        val previewPartner = if (entry.language == Language.TAJIK) mode.partner.code else Language.TAJIK.code

        val homographs = if (entryKey.isNotEmpty()) {
            db.get().rawQuery(
                """
                SELECT e2.id, e2.lang, e2.headword, e2.pos,
                       ${previewColumn("e2", 3, fixedPartner = true)} AS preview
                FROM entries e2
                WHERE e2.lang = ? AND e2.search_key = ? AND e2.id != ?
                ORDER BY e2.frequency DESC, e2.id ASC
                LIMIT 6
                """.trimIndent(),
                arrayOf(previewPartner, entry.language.code, entryKey, id.toString()),
            ).use { c ->
                c.mapRows {
                    SearchResult(
                        id = getLong(0),
                        language = Language.fromCode(getString(1)),
                        headword = getString(2),
                        pos = PartOfSpeech.fromCode(stringOrNull(3)),
                        preview = stringOrNull(4).orEmpty(),
                    )
                }
            }
        } else emptyList()

        val synonyms = db.get().rawQuery(
            """
            SELECT e2.id, e2.lang, e2.headword, e2.pos,
                   ${previewColumn("e2", 2, fixedPartner = true)} AS preview,
                   COUNT(*) AS shared
            FROM translations t1
            JOIN translations t_rev ON t_rev.from_id = t1.to_id
            JOIN entries e2 ON e2.id = t_rev.to_id
            WHERE t1.from_id = ? AND e2.lang = ? AND e2.id != ? AND e2.search_key != ?
            GROUP BY e2.id
            ORDER BY shared DESC, e2.frequency DESC
            LIMIT 8
            """.trimIndent(),
            arrayOf(previewPartner, id.toString(), entry.language.code, id.toString(), entryKey),
        ).use { c ->
            c.mapRows {
                SearchResult(
                    id = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    preview = stringOrNull(4).orEmpty(),
                )
            }
        }

        val synonymIds = synonyms.map { it.id }.toHashSet()
        val homographIds = homographs.map { it.id }.toHashSet()
        val rootKey = when {
            entryKey.length >= 6 -> entryKey.dropLast(1)
            else -> entryKey
        }
        val relatedWords = if (rootKey.length >= 3) {
            db.get().rawQuery(
                """
                SELECT e2.id, e2.lang, e2.headword, e2.pos,
                       ${previewColumn("e2", 2, fixedPartner = true)} AS preview
                FROM entries e2
                WHERE e2.lang = ?
                  AND e2.search_key >= ?
                  AND e2.search_key < ? || char(65535)
                  AND e2.id != ?
                ORDER BY e2.frequency DESC, length(e2.headword) ASC
                LIMIT 12
                """.trimIndent(),
                arrayOf(previewPartner, entry.language.code, rootKey, rootKey, id.toString()),
            ).use { c ->
                c.mapRows {
                    SearchResult(
                        id = getLong(0),
                        language = Language.fromCode(getString(1)),
                        headword = getString(2),
                        pos = PartOfSpeech.fromCode(stringOrNull(3)),
                        preview = stringOrNull(4).orEmpty(),
                    )
                }
            }.filter { it.id !in synonymIds && it.id !in homographIds }.take(8)
        } else emptyList()

        EntryDetail(
            entry = entry,
            translations = translations.groupBy { it.language },
            examples = examples,
            homographs = homographs,
            synonyms = synonyms,
            relatedWords = relatedWords,
        )
    }

    /** Deterministic, offline word of the day — identical algorithm on iOS (§4.4). */
    suspend fun wordOfTheDay(date: LocalDate = LocalDate.now()): Entry? = withContext(io) {
        val count = db.get().rawQuery(
            "SELECT COUNT(*) FROM entries WHERE lang = 'tg' AND frequency >= 50", null,
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        if (count == 0L) return@withContext null
        val offset = Math.floorMod(date.toEpochDay(), count)
        db.get().rawQuery(
            """
            SELECT id, lang, headword, pos, frequency, topic FROM entries
            WHERE lang = 'tg' AND frequency >= 50 ORDER BY id LIMIT 1 OFFSET ?
            """.trimIndent(),
            arrayOf(offset.toString()),
        ).use { c -> c.mapRows { toEntry() }.firstOrNull() }
    }

    /** Short translation preview for an entry (used by Word of the Day, history, favorites). */
    suspend fun preview(id: Long, mode: DictionaryMode): String = withContext(io) {
        db.get().rawQuery(
            "SELECT ${previewColumn("e", 4)} FROM entries e WHERE e.id = ?",
            arrayOf(mode.partner.code, id.toString()),
        ).use { c -> if (c.moveToFirst()) c.stringOrNull(0).orEmpty() else "" }
    }

    suspend fun info(): DictionaryInfo = withContext(io) {
        val meta = db.get().rawQuery("SELECT key, value FROM metadata", null).use { c ->
            c.mapRows { getString(0) to getString(1) }.toMap()
        }
        DictionaryInfo(meta["data_version"].orEmpty(), meta["stats"].orEmpty())
    }

    /**
     * Resolves the current entry ID for `(lang, headword)` after a `dictionary.sqlite` update.
     * Lookup order:
     *  1. Exact case-insensitive `(lang, headword)` match (highest frequency first).
     *  2. Normalized `(lang, search_key)` match.
     *  3. Modern post-1998 Tajik orthography fallback for pre-1998 Soviet Tajik headwords (`ц, щ, ы, ь`).
     */
    suspend fun resolveEntryId(lang: String, headword: String): Long? = withContext(io) {
        val trimmed = headword.trim()
        if (trimmed.isEmpty()) return@withContext null

        queryIdByHeadwordOrKey(lang, trimmed)?.let { return@withContext it }

        if (lang == Language.TAJIK.code && containsSovietTajikChars(trimmed)) {
            val modernized = modernizeSovietTajik(trimmed)
            if (modernized.isNotEmpty() && !modernized.equals(trimmed, ignoreCase = true)) {
                queryIdByHeadwordOrKey(lang, modernized)?.let { return@withContext it }
            }
        }
        null
    }

    private fun queryIdByHeadwordOrKey(lang: String, headword: String): Long? {
        db.get().rawQuery(
            "SELECT id FROM entries WHERE lang = ? AND LOWER(headword) = LOWER(?) ORDER BY frequency DESC, id ASC LIMIT 1",
            arrayOf(lang, headword),
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val key = TextNormalizer.searchKey(headword)
        if (key.isEmpty()) return null
        db.get().rawQuery(
            "SELECT id FROM entries WHERE lang = ? AND search_key = ? ORDER BY frequency DESC, id ASC LIMIT 1",
            arrayOf(lang, key),
        ).use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    /** Returns frequency-ordered Tajik candidates (plus optional cloze example) for the word-teaching game. */
    suspend fun learningCandidates(
        topic: String? = null,
        mode: DictionaryMode,
        maxFrequency: Int = 100,
        limit: Int = 450,
    ): List<WordCardCandidate> = withContext(io) {
        val partnerCol = if (mode.partner == Language.RUSSIAN) "ex.ru" else "ex.en"
        val topicClause = if (topic == null) "" else "AND e.topic = ?"
        val freqClause = if (maxFrequency < 100) "AND e.frequency <= ?" else ""
        val sql = """
            SELECT e.id, e.lang, e.headword, e.pos, e.frequency, e.topic,
                   ${previewColumn("e", 4, fixedPartner = true)} AS preview,
                   (SELECT ex.tg FROM examples ex WHERE ex.entry_id = e.id LIMIT 1) AS ex_tg,
                   (SELECT $partnerCol FROM examples ex WHERE ex.entry_id = e.id LIMIT 1) AS ex_partner
            FROM entries e
            WHERE e.lang = 'tg' AND e.frequency >= 40 $topicClause $freqClause
            ORDER BY e.frequency DESC, e.id ASC
            LIMIT ?
        """.trimIndent()
        val args = buildList {
            add(mode.partner.code)
            if (topic != null) add(topic)
            if (maxFrequency < 100) add(maxFrequency.toString())
            add(limit.toString())
        }.toTypedArray()

        db.get().rawQuery(sql, args).use { c ->
            c.mapRows {
                WordCardCandidate(
                    entryId = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    preview = stringOrNull(6).orEmpty(),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    frequency = getInt(4),
                    topic = stringOrNull(5),
                    exampleTg = stringOrNull(7),
                    examplePartner = stringOrNull(8),
                )
            }
        }
    }

    /** Returns live headword counts across frequency tiers in `dictionary.sqlite`. */
    suspend fun lexiconStats(): LexiconStats = withContext(io) {
        val sql = """
            SELECT COUNT(*),
                   SUM(CASE WHEN frequency >= 90 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN frequency BETWEEN 80 AND 89 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN frequency BETWEEN 70 AND 79 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN frequency < 70 THEN 1 ELSE 0 END)
            FROM entries
            WHERE lang = 'tg'
        """.trimIndent()
        db.get().rawQuery(sql, null).use { c ->
            if (c.moveToFirst()) {
                LexiconStats(
                    totalTajik = c.getInt(0),
                    tier90Count = c.getInt(1),
                    tier80Count = c.getInt(2),
                    tier70Count = c.getInt(3),
                    tierOtherCount = c.getInt(4),
                )
            } else {
                LexiconStats.FALLBACK
            }
        }
    }

    /** Samples stratified Tajik entries across all 4 frequency tiers for the quick level placement test. */
    suspend fun placementPool(mode: DictionaryMode): List<WordCardCandidate> = withContext(io) {
        val ranges = listOf(90 to 100, 80 to 89, 70 to 79, 40 to 69)
        val combined = ArrayList<WordCardCandidate>(100)
        for ((lo, hi) in ranges) {
            val sql = """
                SELECT e.id, e.lang, e.headword, e.pos, e.frequency, e.topic,
                       ${previewColumn("e", 2, fixedPartner = true)} AS preview
                FROM entries e
                WHERE e.lang = 'tg' AND e.frequency BETWEEN ? AND ?
                ORDER BY RANDOM()
                LIMIT 25
            """.trimIndent()
            db.get().rawQuery(
                sql,
                arrayOf(mode.partner.code, lo.toString(), hi.toString()),
            ).use { c ->
                c.mapRows {
                    WordCardCandidate(
                        entryId = getLong(0),
                        language = Language.fromCode(getString(1)),
                        headword = getString(2),
                        preview = stringOrNull(6).orEmpty(),
                        pos = PartOfSpeech.fromCode(stringOrNull(3)),
                        frequency = getInt(4),
                        topic = stringOrNull(5),
                        exampleTg = null,
                        examplePartner = null,
                    )
                }.let { combined.addAll(it) }
            }
        }
        combined
    }

    suspend fun learningCandidatesForIds(
        entryIds: List<Long>,
        mode: DictionaryMode,
    ): List<WordCardCandidate> = withContext(io) {
        if (entryIds.isEmpty()) return@withContext emptyList()
        val sql = learningCandidatesForIdsSql(entryIds.size, mode)
        val args = buildList {
            add(mode.partner.code)
            entryIds.forEach { add(it.toString()) }
        }.toTypedArray()

        db.get().rawQuery(sql, args).use { c ->
            c.mapRows {
                WordCardCandidate(
                    entryId = getLong(0),
                    language = Language.fromCode(getString(1)),
                    headword = getString(2),
                    preview = stringOrNull(6).orEmpty(),
                    pos = PartOfSpeech.fromCode(stringOrNull(3)),
                    frequency = getInt(4),
                    topic = stringOrNull(5),
                    exampleTg = stringOrNull(7),
                    examplePartner = stringOrNull(8),
                )
            }.filter { it.preview.isNotBlank() }
        }
    }

    suspend fun topTajikHeadwords(limit: Int = 60): List<String> = withContext(io) {
        db.get().rawQuery(
            "SELECT headword FROM entries WHERE lang = 'tg' ORDER BY frequency DESC, id ASC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> c.mapRows { getString(0) } }
    }

    companion object {
        private val SOVIET_TAJIK_RE = Regex("[цщыьЦЩЫЬ]")
        private val ARABIC_SOFT_SIGN_RE = Regex("([ғқҳҷзбдтсшрлнмвфкпг])ь([ие])", RegexOption.IGNORE_CASE)
        private val TSIYA_RE = Regex("(.)(ция\\w*|цион\\w*)", RegexOption.IGNORE_CASE)
        private val TAJIK_VOWELS = setOf('а', 'о', 'у', 'э', 'и', 'і', 'ї', 'е', 'ё', 'ю', 'я', 'ӯ')
        private val EXPLICIT_MODERN_TAJIK = mapOf(
            "лыжа" to "лижа",
            "лыжатозӣ" to "лижатозӣ",
            "циркуль" to "паргор",
            "цирк" to "сирк",
            "цикл" to "сикл",
            "цистерна" to "систерна",
            "цитата" to "иқтибос",
            "цемент" to "семент",
            "цементӣ" to "сементӣ",
            "цех" to "сех",
            "центр" to "марказ",
            "концерт" to "консерт",
            "концертӣ" to "консертӣ",
            "концерн" to "консерн",
            "канцелярия" to "канселярия",
            "канцеляриявӣ" to "канселяриявӣ",
            "канцелярӣ" to "канселярӣ",
            "рецензия" to "тақриз",
            "офицер" to "афсар",
            "принцип" to "принсип",
            "медицина" to "тиб",
            "медицинӣ" to "тиббӣ",
            "дисциплина" to "интизом",
            "лицей" to "литсей",
            "социалист" to "сотсиалист",
            "социалистӣ" to "сотсиалистӣ",
            "социалӣ" to "сотсиалӣ",
            "нацист" to "натсист",
            "нацистӣ" to "натсистӣ",
            "публицист" to "публитсист",
            "публицистӣ" to "публитсистӣ",
            "процент" to "фоиз",
            "доцент" to "дотсент",
            "акцент" to "аксент",
            "абзац" to "абзатс",
            "лоцман" to "лотсман",
            "швейцариягӣ" to "швейтсариягӣ",
            "швейцарӣ" to "швейтсарӣ",
            "французӣ" to "фаронсавӣ",
            "интернационал" to "интернатсионал",
            "интернационалӣ" to "интернатсионалӣ",
            "энциклопедия" to "энсиклопедия",
            "энциклопедӣ" to "энсиклопедӣ",
            "ассоциация" to "ассотсиатсия",
            "концентрация" to "консентратсия",
            "концентрационӣ" to "консентратсионӣ",
            "концентрат" to "консентрат",
            "милиционер" to "милитсионер",
            "революционер" to "револютсионер",
            "пьеса" to "песа",
            "эскадрилья" to "эскадриля",
            "батальон" to "баталон",
            "интервью" to "интервю",
            "судья" to "судя",
            "фильм" to "филм",
            "кинофильм" to "кинофилм",
            "роль" to "рол",
            "руль" to "рул",
            "гриль" to "грил",
            "пальто" to "палто",
            "тюрьма" to "маҳбас",
            "мадьюнӣ" to "мадюнӣ",
        )

        internal fun containsSovietTajikChars(text: String): Boolean = SOVIET_TAJIK_RE.containsMatchIn(text)

        /** Converts pre-1998 Soviet Tajik orthography (`бисьёр`, `тағьир`, `коллекция`, `революция`) to modern Tajik. */
        internal fun modernizeSovietTajik(raw: String): String {
            val w0 = raw.trim()
            EXPLICIT_MODERN_TAJIK[w0.lowercase()]?.let { return it }
            var w = ARABIC_SOFT_SIGN_RE.replace(w0) { m -> "${m.groupValues[1]}й${m.groupValues[2]}" }
            w = w.replace("ь", "").replace("Ь", "")
            w = TSIYA_RE.replace(w) { m ->
                val prev = m.groupValues[1]
                val suf = m.groupValues[2]
                val isTsiya = suf.lowercase().startsWith("ция")
                val stem = if (prev.lowercase().firstOrNull() in TAJIK_VOWELS) {
                    if (isTsiya) "тсия" else "тсион"
                } else {
                    if (isTsiya) "сия" else "сион"
                }
                prev + stem + suf.drop(4)
            }
            w = w.replace("ц", "тс").replace("Ц", "Тс")
                .replace("щ", "ш").replace("Щ", "Ш")
                .replace("ы", "и").replace("Ы", "И")
            return w
        }
        /**
         * Word-by-word phrase fallback: each word of a multi-word [key] (up to 8, length >= 2, in query
         * order) is looked up as an exact search key (3 hits), then by its stems (2), then - for words
         * of 3+ letters - by prefix (2). Duplicate entries are skipped. Mirrors iOS `wordByWordSearch`.
         */
        internal fun wordByWord(
            key: String,
            languages: Set<Language>,
            limit: Int,
            exact: (keys: List<String>, limit: Int) -> List<SearchResult>,
            prefix: (word: String, limit: Int) -> List<SearchResult>,
        ): List<SearchResult> {
            val words = key.split(' ').filter { it.isNotEmpty() }
            if (words.size < 2) return emptyList()

            val seen = HashSet<Long>()
            val combined = ArrayList<SearchResult>()
            for (word in words.take(8)) {
                if (word.length < 2) continue
                var hits = exact(listOf(word), 3)
                if (hits.isEmpty()) {
                    val stems = WordStemmer.candidateStems(word, languages)
                    if (stems.isNotEmpty()) hits = exact(stems, 2)
                }
                if (hits.isEmpty() && word.length >= 3) hits = prefix(word, 2)
                for (hit in hits) if (seen.add(hit.id)) combined += hit
            }
            return combined.take(limit)
        }

        /** Columns: id, lang, headword, pos, preview. Bindings: [exactMatchesArgs]. */
        internal fun exactMatchesSql(keyCount: Int, langCount: Int, limit: Int): String = """
            SELECT e.id, e.lang, e.headword, e.pos,
                   ${previewColumn("e", 4)} AS preview
            FROM entries e
            WHERE e.lang IN (${List(langCount) { "?" }.joinToString(",")})
              AND e.search_key IN (${List(keyCount) { "?" }.joinToString(",")})
            ORDER BY e.frequency DESC, e.headword ASC
            LIMIT $limit
        """.trimIndent()

        internal fun exactMatchesArgs(keys: List<String>, languages: Set<Language>, mode: DictionaryMode): Array<String> =
            buildList {
                add(mode.partner.code)
                languages.forEach { add(it.code) }
                keys.forEach { add(it) }
            }.toTypedArray()

        /** Columns: id, lang, headword, pos, preview. Bindings: [prefixSearchArgs]. */
        internal fun prefixSearchSql(langCount: Int, limit: Int): String = """
            SELECT e.id, e.lang, e.headword, e.pos,
                   ${previewColumn("e", 4)} AS preview
            FROM entries e
            WHERE e.lang IN (${List(langCount) { "?" }.joinToString(",")})
              AND e.search_key >= ?
              AND e.search_key < ? || char(65535)
            ORDER BY (e.search_key = ?) DESC, e.frequency DESC, length(e.headword) ASC, e.headword ASC
            LIMIT $limit
        """.trimIndent()

        internal fun prefixSearchArgs(key: String, languages: Set<Language>, mode: DictionaryMode): Array<String> =
            buildList {
                add(mode.partner.code)
                languages.forEach { add(it.code) }
                add(key); add(key); add(key)
            }.toTypedArray()

        /**
         * Correlated subquery yielding a short `", "`-joined translation preview for the entry aliased [alias].
         * Consumes exactly one `?` binding: the partner language code.
         * - By default the preview language is the partner for Tajik entries and Tajik for RU/EN entries;
         *   [fixedPartner] always uses the bound language.
         * - Translations with the same spelling (homograph targets like two "ташаккур" entries) are
         *   collapsed so previews never read "ташаккур, раҳмат, ташаккур". Same SQL as iOS `previewColumn`.
         */
        internal fun learningCandidatesForIdsSql(idCount: Int, mode: DictionaryMode): String {
            val partnerCol = if (mode.partner == Language.RUSSIAN) "ex.ru" else "ex.en"
            val placeholders = List(idCount) { "?" }.joinToString(",")
            return """
                SELECT req.id, e.lang, e.headword, e.pos, e.frequency, e.topic,
                       ${previewColumn("e", 4, fixedPartner = true)} AS preview,
                       (SELECT ex.tg FROM examples ex WHERE ex.entry_id = e.id LIMIT 1) AS ex_tg,
                       (SELECT $partnerCol FROM examples ex WHERE ex.entry_id = e.id LIMIT 1) AS ex_partner
                FROM entries req
                JOIN entries e ON e.id = CASE
                    WHEN req.lang = 'tg' THEN req.id
                    ELSE (
                        SELECT t.to_id FROM translations t
                        JOIN entries pe ON pe.id = t.to_id
                        WHERE t.from_id = req.id AND pe.lang = 'tg'
                        ORDER BY t.position ASC LIMIT 1
                    )
                END
                WHERE req.id IN ($placeholders)
                ORDER BY e.frequency DESC, req.id ASC
            """.trimIndent()
        }

        internal fun previewColumn(alias: String, limit: Int, fixedPartner: Boolean = false): String {
            val langCondition = if (fixedPartner) "?" else "CASE WHEN $alias.lang = 'tg' THEN ? ELSE 'tg' END"
            return """
                (SELECT group_concat(h, ', ') FROM (
                    SELECT pe.headword AS h, MIN(pt.position) AS p, MIN(pt.id) AS first_id
                    FROM translations pt JOIN entries pe ON pe.id = pt.to_id
                    WHERE pt.from_id = $alias.id AND pe.lang = $langCondition
                    GROUP BY pe.headword
                    ORDER BY p, first_id
                    LIMIT $limit))
            """.trimIndent()
        }
    }

    private fun Cursor.toEntry() = Entry(
        id = getLong(0),
        language = Language.fromCode(getString(1)),
        headword = getString(2),
        pos = PartOfSpeech.fromCode(stringOrNull(3)),
        frequency = getInt(4),
        topic = stringOrNull(5),
    )
}

private inline fun <T> Cursor.mapRows(transform: Cursor.() -> T): List<T> {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += transform()
    return out
}

private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

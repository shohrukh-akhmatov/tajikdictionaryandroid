package tj.tajikdictionary.data.dictionary

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import tj.tajikdictionary.core.text.TextNormalizer
import tj.tajikdictionary.data.user.FavoriteEntity
import tj.tajikdictionary.data.user.FlashcardEntity
import tj.tajikdictionary.data.user.HistoryEntity
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.AnswerCheckResult
import tj.tajikdictionary.domain.model.ChallengeKind
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.PartOfSpeech
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.domain.model.WordCardCandidate
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.domain.model.WordProgressSnapshot
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the repository's own SQL builders and phrase-fallback loop against the bundled
 * `dictionary.sqlite` through JDBC (no device needed).
 */
class BundledDictionarySearchTest {

    companion object {
        private lateinit var conn: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val db = listOf("src/main/assets/dictionary.sqlite", "app/src/main/assets/dictionary.sqlite")
                .map(::File).first { it.exists() }
            conn = DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}?open_mode=1")
        }

        @AfterClass
        @JvmStatic
        fun close() = conn.close()
    }

    private fun query(sql: String, args: Array<String>): List<SearchResult> =
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SearchResult(
                                id = rs.getLong(1),
                                language = Language.fromCode(rs.getString(2)),
                                headword = rs.getString(3),
                                pos = PartOfSpeech.fromCode(rs.getString(4)),
                                preview = rs.getString(5).orEmpty(),
                            ),
                        )
                    }
                }
            }
        }

    private fun exact(keys: List<String>, languages: Set<Language>, mode: DictionaryMode, limit: Int) = query(
        DictionaryRepository.exactMatchesSql(keys.size, languages.size, limit),
        DictionaryRepository.exactMatchesArgs(keys, languages, mode),
    )

    private fun prefix(key: String, languages: Set<Language>, mode: DictionaryMode, limit: Int) = query(
        DictionaryRepository.prefixSearchSql(languages.size, limit),
        DictionaryRepository.prefixSearchArgs(key, languages, mode),
    )

    private fun wordByWord(raw: String, language: Language, mode: DictionaryMode): List<String> {
        val langs = setOf(language)
        return DictionaryRepository.wordByWord(
            TextNormalizer.searchKey(raw), langs, limit = 50,
            exact = { keys, n -> exact(keys, langs, mode, n) },
            prefix = { word, n -> prefix(word, langs, mode, n) },
        ).map { it.headword.lowercase() }
    }

    @Test
    fun previewCollapsesDuplicateTranslations() {
        val hits = exact(listOf(TextNormalizer.searchKey("спасибо")), setOf(Language.RUSSIAN), DictionaryMode.TJ_RU, 3)
        val spasibo = hits.first { it.headword.lowercase() == "спасибо" }
        assertEquals("ташаккур, раҳмат, қуллуқ", spasibo.preview)
    }

    @Test
    fun previewsNeverRepeatAHeadword() {
        val sql = """
            SELECT e.headword, ${DictionaryRepository.previewColumn("e", 4)}
            FROM entries e WHERE e.lang = 'ru' ORDER BY e.frequency DESC LIMIT 2000
        """.trimIndent()
        conn.prepareStatement(sql).use { st ->
            st.setString(1, "ru")
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    val parts = rs.getString(2)?.split(", ") ?: continue
                    assertEquals("duplicate in preview of ${rs.getString(1)}: $parts", parts.size, parts.toSet().size)
                }
            }
        }
    }

    @Test
    fun russianPhraseFallsBackToEachWord() {
        val words = wordByWord("добрый день", Language.RUSSIAN, DictionaryMode.TJ_RU)
        assertTrue("got $words", words.indexOf("добрый") >= 0)
        assertTrue("got $words", words.indexOf("день") > words.indexOf("добрый"))
    }

    @Test
    fun englishPhraseFallsBackToEachWord() {
        val words = wordByWord("good morning", Language.ENGLISH, DictionaryMode.TJ_EN)
        assertTrue("got $words", words.indexOf("good") >= 0)
        assertTrue("got $words", words.indexOf("morning") > words.indexOf("good"))
    }

    @Test
    fun singleWordGetsNoPhraseFallback() {
        assertTrue(wordByWord("день", Language.RUSSIAN, DictionaryMode.TJ_RU).isEmpty())
    }

    @Test
    fun bundledDatabaseHasZeroSovietTajikCharactersAndFixesKnownDefects() {
        // 1. Zero pre-1998 Soviet Tajik letters (ц, щ, ы, ь) in Tajik headwords
        conn.prepareStatement("SELECT headword FROM entries WHERE lang = 'tg'").use { st ->
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    val hw = rs.getString(1)
                    assertFalse(
                        "Soviet character found in Tajik headword: $hw",
                        DictionaryRepository.containsSovietTajikChars(hw),
                    )
                }
            }
        }

        // 2. Common school translations rank first (e.g. 'water' -> 'об', 'to conclude' -> 'бастан', 'happy' -> 'хушбахт')
        val water = exact(listOf(TextNormalizer.searchKey("water")), setOf(Language.ENGLISH), DictionaryMode.TJ_EN, 3)
        assertTrue(water.first { it.headword.lowercase() == "water" }.preview.startsWith("об"))

        val conclude = exact(listOf(TextNormalizer.searchKey("to conclude")), setOf(Language.ENGLISH), DictionaryMode.TJ_EN, 3)
        assertTrue(conclude.first { it.headword.lowercase() == "to conclude" }.preview.startsWith("бастан"))

        val happy = exact(listOf(TextNormalizer.searchKey("happy")), setOf(Language.ENGLISH), DictionaryMode.TJ_EN, 3)
        val happyHit = happy.first { it.headword.lowercase() == "happy" }
        assertEquals(PartOfSpeech.ADJECTIVE, happyHit.pos)
        assertTrue(happyHit.preview.startsWith("хушбахт"))
    }

    @Test
    fun sovietTajikModernizerAndUserDataMergeHelpersWork() {
        assertEquals("бисёр", DictionaryRepository.modernizeSovietTajik("бисьёр"))
        assertEquals("тағйир", DictionaryRepository.modernizeSovietTajik("тағьир"))
        assertEquals("коллексия", DictionaryRepository.modernizeSovietTajik("коллекция"))
        assertEquals("револютсия", DictionaryRepository.modernizeSovietTajik("революция"))
        assertEquals("литсей", DictionaryRepository.modernizeSovietTajik("лицей"))
        assertEquals("январ", DictionaryRepository.modernizeSovietTajik("январь"))

        // Verify modernized headwords exist in the bundled database
        for (modern in listOf("бисёр", "тағйир", "коллексия", "револютсия", "литсей", "январ")) {
            val hits = exact(listOf(TextNormalizer.searchKey(modern)), setOf(Language.TAJIK), DictionaryMode.TJ_RU, 3)
            assertTrue("Missing modernized entry '$modern' in bundled DB", hits.any { it.headword.lowercase() == modern })
        }

        // Verify collision merging keeps earliest favorite, latest history, and highest flashcard progress
        val fav1 = FavoriteEntity(10L, "tg", "бисёр", "много", addedAt = 1000L)
        val fav2 = FavoriteEntity(10L, "tg", "бисёр", "много", addedAt = 2000L)
        assertEquals(1000L, UserDataRepository.mergeFavorites(fav2, fav1).addedAt)

        val hist1 = HistoryEntity(10L, "tg", "бисёр", "много", viewedAt = 1000L)
        val hist2 = HistoryEntity(10L, "tg", "бисёр", "много", viewedAt = 3000L)
        assertEquals(3000L, UserDataRepository.mergeHistory(hist1, hist2).viewedAt)

        val cardLow = FlashcardEntity(10L, "tg", "бисёр", "много", repetitions = 1, intervalDays = 1, easeFactor = 2.5, nextReviewAt = 100L, masteryStage = 1, stabilityDays = 1.0)
        val cardHigh = FlashcardEntity(10L, "tg", "бисёр", "много", repetitions = 4, intervalDays = 14, easeFactor = 2.6, nextReviewAt = 500L, masteryStage = 3, stabilityDays = 14.0)
        val mergedCard = UserDataRepository.mergeFlashcards(cardLow, cardHigh)
        assertEquals(3, mergedCard.masteryStage)
        assertEquals(4, mergedCard.repetitions)
        assertEquals(14.0, mergedCard.stabilityDays, 0.001)
    }

    @Test
    fun learningCandidatesForIdsResolvesRussianEntriesToEnglishWhenStudyModeIsEnglish() {
        // Find a Russian entry ("дом") and verify learningCandidatesForIdsSql returns its Tajik headword ("хона")
        // paired with English translations when studyMode == TJ_EN (never Russian!).
        val domHits = exact(listOf(TextNormalizer.searchKey("дом")), setOf(Language.RUSSIAN), DictionaryMode.TJ_RU, 3)
        val domId = domHits.first { it.headword.lowercase() == "дом" }.id

        val sql = DictionaryRepository.learningCandidatesForIdsSql(1, DictionaryMode.TJ_EN)
        conn.prepareStatement(sql).use { st ->
            st.setString(1, DictionaryMode.TJ_EN.partner.code)
            st.setString(2, domId.toString())
            st.executeQuery().use { rs ->
                assertTrue(rs.next())
                assertEquals(domId, rs.getLong(1))
                assertEquals("tg", rs.getString(2))
                assertEquals("хона", rs.getString(3))
                val preview = rs.getString(7).orEmpty()
                assertTrue("Expected English preview for 'дом' in TJ_EN mode, got: $preview", preview.contains("house"))
            }
        }
    }

    @Test
    fun lingvistTargetLanguageTrainerGeneratesEnglishChallengesAndAcceptsSynonyms() {
        val candidates = listOf(
            WordCardCandidate(1L, Language.TAJIK, "хона", "house, home, room", PartOfSpeech.NOUN, 95, "home", "Ин хона калон аст.", "This house is big."),
            WordCardCandidate(2L, Language.TAJIK, "рафтан", "to go, to leave", PartOfSpeech.VERB, 96, "verbs", "Ман ба мактаб меравам.", "I go to school."),
            WordCardCandidate(3L, Language.TAJIK, "китоб", "book", PartOfSpeech.NOUN, 94, "education", null, null),
            WordCardCandidate(4L, Language.TAJIK, "об", "water", PartOfSpeech.NOUN, 98, "food", null, null),
            WordCardCandidate(5L, Language.TAJIK, "мактаб", "school", PartOfSpeech.NOUN, 92, "education", null, null),
        )

        val items = WordGameEngine.makeRoundItems(
            selected = candidates.take(2),
            allPool = candidates,
            fallbackDistractors = listOf("салом", "дӯст"), // Cyrillic fallback must be ignored for English target!
            progressById = mapOf(
                2L to WordProgressSnapshot(entryId = 2L, masteryStage = 1, stabilityDays = 1.5, repetitions = 1, lastReviewedAtMs = 1000L, nextReviewAtMs = 2000L),
            ),
        )

        val houseItem = items[0]
        assertEquals(ChallengeKind.MULTIPLE_CHOICE, houseItem.challengeKind)
        assertEquals("house", houseItem.targetAnswer)
        assertEquals("house", houseItem.buildTarget)
        assertTrue(houseItem.choices.contains("house"))
        // None of the synonyms ("home", "room") or Cyrillic words may appear as distractors
        assertFalse(houseItem.choices.contains("home"))
        assertFalse(houseItem.choices.contains("room"))
        assertTrue(houseItem.choices.all { it.all { ch -> ch.code < 0x0400 } })
        assertEquals("This ____ is big.", houseItem.clozeSentence)
        assertEquals("Ин хона калон аст.", houseItem.clozeTranslation)

        val goItem = items[1]
        assertEquals(ChallengeKind.WORD_BUILDER, goItem.challengeKind)
        assertEquals("to go", goItem.targetAnswer)
        assertEquals("go", goItem.buildTarget)
        assertEquals(listOf("o", "g"), goItem.tiles)
        assertEquals("I ____ to school.", goItem.clozeSentence)

        // Synonym and bare-infinitive acceptance in checkTypedAnswer
        assertEquals(
            AnswerCheckResult.Exact,
            WordGameEngine.checkTypedAnswer("house", houseItem.buildTarget, houseItem.acceptedAnswers),
        )
        assertEquals(
            AnswerCheckResult.Exact,
            WordGameEngine.checkTypedAnswer("home", houseItem.buildTarget, houseItem.acceptedAnswers),
        )
        assertEquals(
            AnswerCheckResult.Exact,
            WordGameEngine.checkTypedAnswer("go", goItem.buildTarget, goItem.acceptedAnswers),
        )
        assertEquals(
            AnswerCheckResult.Exact,
            WordGameEngine.checkTypedAnswer("to go", goItem.buildTarget, goItem.acceptedAnswers),
        )
        assertEquals(
            AnswerCheckResult.Exact,
            WordGameEngine.checkTypedAnswer("leave", goItem.buildTarget, goItem.acceptedAnswers),
        )
        // 1-letter typo near-miss on 5+ letter English word
        assertEquals(
            AnswerCheckResult.TajikLetterNearMiss("house"),
            WordGameEngine.checkTypedAnswer("hause", houseItem.buildTarget, houseItem.acceptedAnswers),
        )
    }
}

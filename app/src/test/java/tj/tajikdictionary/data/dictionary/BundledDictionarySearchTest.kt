package tj.tajikdictionary.data.dictionary

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import tj.tajikdictionary.core.text.TextNormalizer
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.PartOfSpeech
import tj.tajikdictionary.domain.model.SearchResult
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
}

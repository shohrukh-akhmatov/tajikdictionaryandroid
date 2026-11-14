package tj.tajikdictionary.data.dictionary

import android.database.Cursor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.tajikdictionary.core.text.LanguageDetector
import tj.tajikdictionary.core.text.TextNormalizer
import tj.tajikdictionary.domain.model.DictionaryInfo
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Entry
import tj.tajikdictionary.domain.model.EntryDetail
import tj.tajikdictionary.domain.model.Example
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.PartOfSpeech
import tj.tajikdictionary.domain.model.SearchOutcome
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.domain.model.Translation
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
            if (languages.isEmpty()) {
                val suggestion = DictionaryMode.entries.firstOrNull { (candidates intersect it.languages).isNotEmpty() }
                return@withContext SearchOutcome(rawQuery, emptyList(), emptySet(), suggestion)
            }

            val placeholders = languages.joinToString(",") { "?" }
            val sql = """
                SELECT e.id, e.lang, e.headword, e.pos,
                       (SELECT group_concat(h, ', ') FROM (
                            SELECT t2.headword AS h
                            FROM translations t JOIN entries t2 ON t2.id = t.to_id
                            WHERE t.from_id = e.id
                              AND t2.lang = CASE WHEN e.lang = 'tg' THEN ? ELSE 'tg' END
                            ORDER BY t.position LIMIT 4)) AS preview
                FROM entries e
                WHERE e.lang IN ($placeholders)
                  AND e.search_key >= ?
                  AND e.search_key < ? || char(65535)
                ORDER BY (e.search_key = ?) DESC, e.frequency DESC, length(e.headword) ASC, e.headword ASC
                LIMIT $limit
            """.trimIndent()
            val args = buildList {
                add(mode.partner.code)
                languages.forEach { add(it.code) }
                add(key); add(key); add(key)
            }.toTypedArray()

            val results = db.get().rawQuery(sql, args).use { c ->
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
            SearchOutcome(rawQuery, results, languages, suggestedMode = null)
        }

    suspend fun entry(id: Long): Entry? = withContext(io) {
        db.get().rawQuery(
            "SELECT id, lang, headword, pos, frequency, topic FROM entries WHERE id = ?",
            arrayOf(id.toString()),
        ).use { c -> c.mapRows { toEntry() }.firstOrNull() }
    }

    suspend fun detail(id: Long): EntryDetail? = withContext(io) {
        val entry = entry(id) ?: return@withContext null
        val translations = db.get().rawQuery(
            """
            SELECT t2.id, t2.lang, t2.headword, t2.pos, t.note
            FROM translations t JOIN entries t2 ON t2.id = t.to_id
            WHERE t.from_id = ? ORDER BY t2.lang, t.position
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
                )
            }
        }
        val examples = db.get().rawQuery(
            "SELECT tg, ru, en FROM examples WHERE entry_id = ?",
            arrayOf(id.toString()),
        ).use { c -> c.mapRows { Example(getString(0), stringOrNull(1), stringOrNull(2)) } }

        EntryDetail(entry, translations.groupBy { it.language }, examples)
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
            """
            SELECT group_concat(h, ', ') FROM (
                SELECT t2.headword AS h FROM translations t
                JOIN entries e ON e.id = t.from_id
                JOIN entries t2 ON t2.id = t.to_id
                WHERE t.from_id = ? AND t2.lang = CASE WHEN e.lang = 'tg' THEN ? ELSE 'tg' END
                ORDER BY t.position LIMIT 4)
            """.trimIndent(),
            arrayOf(id.toString(), mode.partner.code),
        ).use { c -> if (c.moveToFirst()) c.stringOrNull(0).orEmpty() else "" }
    }

    suspend fun info(): DictionaryInfo = withContext(io) {
        val meta = db.get().rawQuery("SELECT key, value FROM metadata", null).use { c ->
            c.mapRows { getString(0) to getString(1) }.toMap()
        }
        DictionaryInfo(meta["data_version"].orEmpty(), meta["stats"].orEmpty())
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

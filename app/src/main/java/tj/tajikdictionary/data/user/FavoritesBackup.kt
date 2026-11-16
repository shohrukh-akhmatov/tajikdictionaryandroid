package tj.tajikdictionary.data.user

/**
 * Pure-Kotlin JSON serializer/parser (v1 favorites-only & v2 full learning backup)
 * using a cross-platform schema shared with the iOS app.
 */
object FavoritesBackup {
    const val CURRENT_VERSION = 2

    data class PlacementBackup(
        val cefrLevel: String,
        val ceilingFreq: Int,
        val baselineWords: Int,
    )

    data class FullBackupData(
        val favorites: List<FavoriteEntity>,
        val flashcards: List<FlashcardEntity>,
        val dailyLogs: List<DailyStatsEntity>,
        val placement: PlacementBackup?,
    )

    fun encode(favorites: List<FavoriteEntity>, exportedAtMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": 1,\n")
        sb.append("  \"exportedAt\": ").append(exportedAtMs).append(",\n")
        appendFavoritesArray(sb, favorites)
        sb.append("\n}\n")
        return sb.toString()
    }

    fun encodeFull(
        favorites: List<FavoriteEntity>,
        flashcards: List<FlashcardEntity>,
        dailyLogs: List<DailyStatsEntity>,
        placement: PlacementBackup?,
        exportedAtMs: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(CURRENT_VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(exportedAtMs).append(",\n")
        appendFavoritesArray(sb, favorites)
        sb.append(",\n  \"flashcards\": [")
        if (flashcards.isNotEmpty()) sb.append('\n')
        flashcards.forEachIndexed { idx, c ->
            sb.append("    {\n")
            sb.append("      \"entryId\": ").append(c.entryId).append(",\n")
            sb.append("      \"lang\": \"").append(escapeJson(c.lang)).append("\",\n")
            sb.append("      \"headword\": \"").append(escapeJson(c.headword)).append("\",\n")
            sb.append("      \"preview\": \"").append(escapeJson(c.preview)).append("\",\n")
            sb.append("      \"repetitions\": ").append(c.repetitions).append(",\n")
            sb.append("      \"intervalDays\": ").append(c.intervalDays).append(",\n")
            sb.append("      \"easeFactor\": ").append(c.easeFactor).append(",\n")
            sb.append("      \"nextReviewAt\": ").append(c.nextReviewAt).append(",\n")
            sb.append("      \"masteryStage\": ").append(c.masteryStage).append(",\n")
            sb.append("      \"stabilityDays\": ").append(c.stabilityDays).append(",\n")
            sb.append("      \"lapses\": ").append(c.lapses).append(",\n")
            sb.append("      \"lastReviewedAt\": ").append(c.lastReviewedAt ?: "null").append(",\n")
            sb.append("      \"isKnownSkipped\": ").append(c.isKnownSkipped).append("\n")
            sb.append("    }")
            if (idx + 1 < flashcards.size) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (flashcards.isNotEmpty()) "  ],\n" else "],\n")
        sb.append("  \"dailyLogs\": [")
        if (dailyLogs.isNotEmpty()) sb.append('\n')
        dailyLogs.forEachIndexed { idx, l ->
            sb.append("    {\n")
            sb.append("      \"dateKey\": \"").append(escapeJson(l.dateKey)).append("\",\n")
            sb.append("      \"wordsReviewed\": ").append(l.wordsReviewed).append(",\n")
            sb.append("      \"wordsLearned\": ").append(l.wordsLearned).append(",\n")
            sb.append("      \"xpEarned\": ").append(l.xpEarned).append(",\n")
            sb.append("      \"roundsCompleted\": ").append(l.roundsCompleted).append(",\n")
            sb.append("      \"studySeconds\": ").append(l.studySeconds).append(",\n")
            sb.append("      \"correctReviews\": ").append(l.correctReviews).append("\n")
            sb.append("    }")
            if (idx + 1 < dailyLogs.size) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (dailyLogs.isNotEmpty()) "  ]" else "]")
        if (placement != null) {
            sb.append(",\n  \"placement\": {\n")
            sb.append("    \"cefrLevel\": \"").append(escapeJson(placement.cefrLevel)).append("\",\n")
            sb.append("    \"ceilingFreq\": ").append(placement.ceilingFreq).append(",\n")
            sb.append("    \"baselineWords\": ").append(placement.baselineWords).append("\n")
            sb.append("  }")
        }
        sb.append("\n}\n")
        return sb.toString()
    }

    private fun appendFavoritesArray(sb: StringBuilder, favorites: List<FavoriteEntity>) {
        sb.append("  \"favorites\": [")
        if (favorites.isNotEmpty()) sb.append('\n')
        favorites.forEachIndexed { idx, item ->
            sb.append("    {\n")
            sb.append("      \"entryId\": ").append(item.entryId).append(",\n")
            sb.append("      \"lang\": \"").append(escapeJson(item.lang)).append("\",\n")
            sb.append("      \"headword\": \"").append(escapeJson(item.headword)).append("\",\n")
            sb.append("      \"preview\": \"").append(escapeJson(item.preview)).append("\",\n")
            sb.append("      \"addedAt\": ").append(item.addedAt).append("\n")
            sb.append("    }")
            if (idx + 1 < favorites.size) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (favorites.isNotEmpty()) "  ]" else "]")
    }

    fun decode(json: String): List<FavoriteEntity> = decodeFull(json).favorites

    fun decodeFull(json: String): FullBackupData {
        val parsed = JsonReader(json).parseValue()
        if (parsed is List<*>) {
            return FullBackupData(parseFavorites(parsed), emptyList(), emptyList(), null)
        }
        val obj = parsed as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object or array")
        @Suppress("UNCHECKED_CAST")
        val favList = (obj["favorites"] as? List<Any?>)
            ?: throw IllegalArgumentException("Missing 'favorites' array")
        @Suppress("UNCHECKED_CAST")
        val cardList = (obj["flashcards"] as? List<Any?>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val logList = (obj["dailyLogs"] as? List<Any?>).orEmpty()
        val placementMap = obj["placement"] as? Map<*, *>
        val placement = placementMap?.let { p ->
            val level = p["cefrLevel"] as? String ?: return@let null
            val ceiling = (p["ceilingFreq"] as? Number)?.toInt() ?: 100
            val baseline = (p["baselineWords"] as? Number)?.toInt() ?: 0
            PlacementBackup(level, ceiling, baseline)
        }
        return FullBackupData(
            favorites = parseFavorites(favList),
            flashcards = parseFlashcards(cardList),
            dailyLogs = parseDailyLogs(logList),
            placement = placement,
        )
    }

    private fun parseFavorites(array: List<Any?>): List<FavoriteEntity> = array.mapNotNull { raw ->
        val obj = raw as? Map<*, *> ?: return@mapNotNull null
        val entryId = (obj["entryId"] as? Number)?.toLong() ?: return@mapNotNull null
        val lang = obj["lang"] as? String ?: return@mapNotNull null
        val headword = obj["headword"] as? String ?: return@mapNotNull null
        if (lang.isBlank() || headword.isBlank()) return@mapNotNull null
        val preview = obj["preview"] as? String ?: ""
        val addedAt = (obj["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        FavoriteEntity(entryId, lang, headword, preview, addedAt)
    }

    private fun parseFlashcards(array: List<Any?>): List<FlashcardEntity> = array.mapNotNull { raw ->
        val obj = raw as? Map<*, *> ?: return@mapNotNull null
        val entryId = (obj["entryId"] as? Number)?.toLong() ?: return@mapNotNull null
        val lang = obj["lang"] as? String ?: return@mapNotNull null
        val headword = obj["headword"] as? String ?: return@mapNotNull null
        if (lang.isBlank() || headword.isBlank()) return@mapNotNull null
        FlashcardEntity(
            entryId = entryId,
            lang = lang,
            headword = headword,
            preview = obj["preview"] as? String ?: "",
            repetitions = (obj["repetitions"] as? Number)?.toInt() ?: 0,
            intervalDays = (obj["intervalDays"] as? Number)?.toInt() ?: 0,
            easeFactor = (obj["easeFactor"] as? Number)?.toDouble() ?: 2.5,
            nextReviewAt = (obj["nextReviewAt"] as? Number)?.toLong() ?: 0L,
            masteryStage = (obj["masteryStage"] as? Number)?.toInt() ?: 0,
            stabilityDays = (obj["stabilityDays"] as? Number)?.toDouble() ?: 0.0,
            lapses = (obj["lapses"] as? Number)?.toInt() ?: 0,
            lastReviewedAt = (obj["lastReviewedAt"] as? Number)?.toLong(),
            isKnownSkipped = (obj["isKnownSkipped"] as? Boolean) ?: false,
        )
    }

    private fun parseDailyLogs(array: List<Any?>): List<DailyStatsEntity> = array.mapNotNull { raw ->
        val obj = raw as? Map<*, *> ?: return@mapNotNull null
        val dateKey = obj["dateKey"] as? String ?: return@mapNotNull null
        if (dateKey.isBlank()) return@mapNotNull null
        DailyStatsEntity(
            dateKey = dateKey,
            wordsReviewed = (obj["wordsReviewed"] as? Number)?.toInt() ?: 0,
            wordsLearned = (obj["wordsLearned"] as? Number)?.toInt() ?: 0,
            xpEarned = (obj["xpEarned"] as? Number)?.toInt() ?: 0,
            roundsCompleted = (obj["roundsCompleted"] as? Number)?.toInt() ?: 0,
            studySeconds = (obj["studySeconds"] as? Number)?.toInt() ?: 0,
            correctReviews = (obj["correctReviews"] as? Number)?.toInt() ?: 0,
        )
    }

    private fun escapeJson(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch.code < 0x20) {
                    out.append("\\u%04x".format(ch.code))
                } else {
                    out.append(ch)
                }
            }
        }
        return out.toString()
    }

    private class JsonReader(private val text: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '${text[pos]}' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < text.length && text[pos] == ',') {
                    pos++
                } else {
                    expect('}')
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list += parseValue()
                skipWhitespace()
                if (pos < text.length && text[pos] == ',') {
                    pos++
                } else {
                    expect(']')
                    break
                }
            }
            return list
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= text.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = text[pos++]) {
                            '"', '\\', '/' -> out.append(esc)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) throw IllegalArgumentException("Invalid \\u escape")
                                out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape \\$esc")
                        }
                    }
                    else -> out.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            var isFloat = false
            if (pos < text.length && text[pos] == '.') {
                isFloat = true
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val raw = text.substring(start, pos)
            return if (isFloat) raw.toDouble() else raw.toLong()
        }

        private fun <T> parseLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, pos)) {
                throw IllegalArgumentException("Expected '$literal' at $pos")
            }
            pos += literal.length
            return value
        }

        private fun expect(ch: Char) {
            if (pos >= text.length || text[pos] != ch) {
                throw IllegalArgumentException("Expected '$ch' at $pos")
            }
            pos++
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}

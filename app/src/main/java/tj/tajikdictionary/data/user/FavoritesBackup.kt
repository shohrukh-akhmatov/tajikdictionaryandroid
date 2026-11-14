package tj.tajikdictionary.data.user

/**
 * Pure-Kotlin JSON serializer/parser for exporting and importing saved favorites
 * using a cross-platform schema shared with the iOS app.
 */
object FavoritesBackup {
    const val CURRENT_VERSION = 1

    fun encode(favorites: List<FavoriteEntity>, exportedAtMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(CURRENT_VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(exportedAtMs).append(",\n")
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
        sb.append(if (favorites.isNotEmpty()) "  ]\n" else "]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun decode(json: String): List<FavoriteEntity> {
        val parsed = JsonReader(json).parseValue()
        val array: List<Any?> = when (parsed) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (parsed["favorites"] as? List<Any?>)
                    ?: throw IllegalArgumentException("Missing 'favorites' array")
            }
            is List<*> -> parsed
            else -> throw IllegalArgumentException("Expected JSON object or array")
        }
        return array.mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            val entryId = (obj["entryId"] as? Number)?.toLong() ?: return@mapNotNull null
            val lang = obj["lang"] as? String ?: return@mapNotNull null
            val headword = obj["headword"] as? String ?: return@mapNotNull null
            if (lang.isBlank() || headword.isBlank()) return@mapNotNull null
            val preview = obj["preview"] as? String ?: ""
            val addedAt = (obj["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            FavoriteEntity(
                entryId = entryId,
                lang = lang,
                headword = headword,
                preview = preview,
                addedAt = addedAt,
            )
        }
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

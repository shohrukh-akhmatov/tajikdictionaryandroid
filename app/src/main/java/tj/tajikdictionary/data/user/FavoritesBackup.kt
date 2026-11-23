package tj.tajikdictionary.data.user

import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.ProfileKind
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot

/**
 * Pure-Kotlin JSON serializer/parser (v1 favorites-only, v2 full learning backup, and
 * cross-platform multi-user `ProfileBackup v1`) using a schema shared with the iOS app.
 */
object FavoritesBackup {
    const val CURRENT_VERSION = 2
    const val PROFILE_BACKUP_FORMAT = "tajikdictionary.profile-backup"
    const val PROFILE_BACKUP_VERSION = 1

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

    data class ProfileSettingsBackup(
        val studyMode: String = DictionaryMode.TJ_EN.name,
        val dailyGoal: Int = 10,
        val placementLevel: String? = null,
        val placementCeilingFreq: Int = 100,
        val placementBaselineWords: Int = 0,
    )

    data class ProfileEntryBackup(
        val profile: LearnerProfile,
        val settings: ProfileSettingsBackup,
        val favorites: List<FavoriteEntity>,
        val flashcards: List<FlashcardEntity>,
        val dailyLogs: List<DailyStatsEntity>,
    )

    data class MultiProfileBackupData(
        val format: String = PROFILE_BACKUP_FORMAT,
        val version: Int = PROFILE_BACKUP_VERSION,
        val exportedAtMs: Long,
        val activeProfileId: String,
        val profiles: List<ProfileEntryBackup>,
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

    fun encodeProfiles(
        profiles: List<ProfileEntryBackup>,
        activeProfileId: String,
        platform: String = "android",
        appVersion: String = "1.0",
        exportedAtMs: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"").append(PROFILE_BACKUP_FORMAT).append("\",\n")
        sb.append("  \"version\": ").append(PROFILE_BACKUP_VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(exportedAtMs).append(",\n")
        sb.append("  \"activeProfileId\": \"").append(escapeJson(activeProfileId)).append("\",\n")
        sb.append("  \"app\": { \"platform\": \"").append(escapeJson(platform))
            .append("\", \"version\": \"").append(escapeJson(appVersion)).append("\" },\n")
        sb.append("  \"profiles\": [")
        if (profiles.isNotEmpty()) sb.append('\n')
        profiles.forEachIndexed { pIdx, entry ->
            val p = entry.profile
            val s = entry.settings
            sb.append("    {\n")
            sb.append("      \"profile\": {\n")
            sb.append("        \"id\": \"").append(escapeJson(p.id)).append("\",\n")
            sb.append("        \"name\": \"").append(escapeJson(p.name)).append("\",\n")
            sb.append("        \"avatar\": \"").append(escapeJson(p.avatar)).append("\",\n")
            sb.append("        \"color\": \"").append(escapeJson(p.colorHex)).append("\",\n")
            sb.append("        \"kind\": \"").append(escapeJson(p.kind.code)).append("\",\n")
            sb.append("        \"createdAt\": ").append(p.createdAtMs).append("\n")
            sb.append("      },\n")
            sb.append("      \"settings\": {\n")
            sb.append("        \"studyMode\": \"").append(escapeJson(s.studyMode)).append("\",\n")
            sb.append("        \"dailyGoal\": ").append(s.dailyGoal).append(",\n")
            sb.append("        \"placementLevel\": ").append(s.placementLevel?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",\n")
            sb.append("        \"placementCeilingFreq\": ").append(s.placementCeilingFreq).append(",\n")
            sb.append("        \"placementBaselineWords\": ").append(s.placementBaselineWords).append("\n")
            sb.append("      },\n")
            sb.append("      \"favorites\": [")
            if (entry.favorites.isNotEmpty()) sb.append('\n')
            entry.favorites.forEachIndexed { idx, item ->
                sb.append("        {\n")
                sb.append("          \"entryId\": ").append(item.entryId).append(",\n")
                sb.append("          \"lang\": \"").append(escapeJson(item.lang)).append("\",\n")
                sb.append("          \"headword\": \"").append(escapeJson(item.headword)).append("\",\n")
                sb.append("          \"preview\": \"").append(escapeJson(item.preview)).append("\",\n")
                sb.append("          \"addedAt\": ").append(item.addedAt).append("\n")
                sb.append("        }")
                if (idx + 1 < entry.favorites.size) sb.append(',')
                sb.append('\n')
            }
            sb.append(if (entry.favorites.isNotEmpty()) "      ],\n" else "],\n")
            sb.append("      \"progress\": [")
            if (entry.flashcards.isNotEmpty()) sb.append('\n')
            entry.flashcards.forEachIndexed { idx, c ->
                sb.append("        {\n")
                sb.append("          \"entryId\": ").append(c.entryId).append(",\n")
                sb.append("          \"lang\": \"").append(escapeJson(c.lang)).append("\",\n")
                sb.append("          \"headword\": \"").append(escapeJson(c.headword)).append("\",\n")
                sb.append("          \"preview\": \"").append(escapeJson(c.preview)).append("\",\n")
                sb.append("          \"repetitions\": ").append(c.repetitions).append(",\n")
                sb.append("          \"intervalDays\": ").append(c.intervalDays).append(",\n")
                sb.append("          \"easeFactor\": ").append(c.easeFactor).append(",\n")
                sb.append("          \"nextReviewAt\": ").append(c.nextReviewAt).append(",\n")
                sb.append("          \"masteryStage\": ").append(c.masteryStage).append(",\n")
                sb.append("          \"stabilityDays\": ").append(c.stabilityDays).append(",\n")
                sb.append("          \"lapses\": ").append(c.lapses).append(",\n")
                sb.append("          \"lastReviewedAt\": ").append(c.lastReviewedAt ?: "null").append(",\n")
                sb.append("          \"isKnownSkipped\": ").append(c.isKnownSkipped).append("\n")
                sb.append("        }")
                if (idx + 1 < entry.flashcards.size) sb.append(',')
                sb.append('\n')
            }
            sb.append(if (entry.flashcards.isNotEmpty()) "      ],\n" else "],\n")
            sb.append("      \"dailyLogs\": [")
            if (entry.dailyLogs.isNotEmpty()) sb.append('\n')
            entry.dailyLogs.forEachIndexed { idx, l ->
                sb.append("        {\n")
                sb.append("          \"dateKey\": \"").append(escapeJson(l.dateKey)).append("\",\n")
                sb.append("          \"wordsReviewed\": ").append(l.wordsReviewed).append(",\n")
                sb.append("          \"wordsLearned\": ").append(l.wordsLearned).append(",\n")
                sb.append("          \"xpEarned\": ").append(l.xpEarned).append(",\n")
                sb.append("          \"roundsCompleted\": ").append(l.roundsCompleted).append(",\n")
                sb.append("          \"studySeconds\": ").append(l.studySeconds).append(",\n")
                sb.append("          \"correctReviews\": ").append(l.correctReviews).append("\n")
                sb.append("        }")
                if (idx + 1 < entry.dailyLogs.size) sb.append(',')
                sb.append('\n')
            }
            sb.append(if (entry.dailyLogs.isNotEmpty()) "      ]\n" else "]\n")
            sb.append("    }")
            if (pIdx + 1 < profiles.size) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (profiles.isNotEmpty()) "  ]\n" else "]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun decodeProfiles(json: String): MultiProfileBackupData {
        val parsed = JsonReader(json).parseValue()
        if (parsed is Map<*, *> && parsed["profiles"] is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val rawProfiles = parsed["profiles"] as List<Any?>
            val exportedAt = (parsed["exportedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val activeId = parsed["activeProfileId"] as? String
            val entries = rawProfiles.mapNotNull { raw ->
                val obj = raw as? Map<*, *> ?: return@mapNotNull null
                val profMap = obj["profile"] as? Map<*, *>
                val profile = parseLearnerProfile(profMap) ?: LearnerProfile.defaultProfile()
                val settingsMap = obj["settings"] as? Map<*, *>
                val settings = ProfileSettingsBackup(
                    studyMode = (settingsMap?.get("studyMode") as? String) ?: DictionaryMode.TJ_EN.name,
                    dailyGoal = (settingsMap?.get("dailyGoal") as? Number)?.toInt() ?: profile.kind.defaultDailyGoal,
                    placementLevel = settingsMap?.get("placementLevel") as? String,
                    placementCeilingFreq = (settingsMap?.get("placementCeilingFreq") as? Number)?.toInt() ?: 100,
                    placementBaselineWords = (settingsMap?.get("placementBaselineWords") as? Number)?.toInt() ?: 0,
                )
                @Suppress("UNCHECKED_CAST")
                val favList = (obj["favorites"] as? List<Any?>).orEmpty()
                @Suppress("UNCHECKED_CAST")
                val cardList = ((obj["progress"] ?: obj["flashcards"]) as? List<Any?>).orEmpty()
                @Suppress("UNCHECKED_CAST")
                val logList = (obj["dailyLogs"] as? List<Any?>).orEmpty()
                ProfileEntryBackup(
                    profile = profile,
                    settings = settings,
                    favorites = parseFavorites(favList),
                    flashcards = parseFlashcards(cardList),
                    dailyLogs = parseDailyLogs(logList),
                )
            }
            val resolvedActive = activeId?.takeIf { id -> entries.any { it.profile.id == id } }
                ?: entries.firstOrNull()?.profile?.id
                ?: LearnerProfile.DEFAULT_PROFILE_ID
            return MultiProfileBackupData(
                exportedAtMs = exportedAt,
                activeProfileId = resolvedActive,
                profiles = entries,
            )
        }
        // Fallback: legacy single-profile backup (v1 or v2).
        val legacy = decodeFull(json)
        val defProfile = LearnerProfile.defaultProfile()
        val settings = ProfileSettingsBackup(
            studyMode = DictionaryMode.TJ_EN.name,
            dailyGoal = 10,
            placementLevel = legacy.placement?.cefrLevel,
            placementCeilingFreq = legacy.placement?.ceilingFreq ?: 100,
            placementBaselineWords = legacy.placement?.baselineWords ?: 0,
        )
        return MultiProfileBackupData(
            exportedAtMs = System.currentTimeMillis(),
            activeProfileId = defProfile.id,
            profiles = listOf(
                ProfileEntryBackup(
                    profile = defProfile,
                    settings = settings,
                    favorites = legacy.favorites,
                    flashcards = legacy.flashcards,
                    dailyLogs = legacy.dailyLogs,
                ),
            ),
        )
    }

    fun encodeProfileList(profiles: List<LearnerProfile>): String {
        val sb = StringBuilder("[")
        profiles.forEachIndexed { idx, p ->
            if (idx > 0) sb.append(',')
            sb.append('{')
            sb.append("\"id\":\"").append(escapeJson(p.id)).append("\",")
            sb.append("\"name\":\"").append(escapeJson(p.name)).append("\",")
            sb.append("\"avatar\":\"").append(escapeJson(p.avatar)).append("\",")
            sb.append("\"color\":\"").append(escapeJson(p.colorHex)).append("\",")
            sb.append("\"kind\":\"").append(escapeJson(p.kind.code)).append("\",")
            sb.append("\"createdAt\":").append(p.createdAtMs)
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decodeProfileList(json: String?): List<LearnerProfile> {
        if (json.isNullOrBlank()) return listOf(LearnerProfile.defaultProfile())
        val parsed = runCatching { JsonReader(json).parseValue() as? List<*> }.getOrNull()
            ?: return listOf(LearnerProfile.defaultProfile())
        val list = parsed.mapNotNull { raw -> parseLearnerProfile(raw as? Map<*, *>) }
        return list.ifEmpty { listOf(LearnerProfile.defaultProfile()) }
    }

    fun encodeProfileStats(stats: Map<String, ProfileStatsSnapshot>): String {
        val sb = StringBuilder("[")
        stats.values.forEachIndexed { idx, s ->
            if (idx > 0) sb.append(',')
            sb.append('{')
            sb.append("\"profileId\":\"").append(escapeJson(s.profileId)).append("\",")
            sb.append("\"currentStreakDays\":").append(s.currentStreakDays).append(',')
            sb.append("\"longestStreakDays\":").append(s.longestStreakDays).append(',')
            sb.append("\"totalXp\":").append(s.totalXp).append(',')
            sb.append("\"masteredCount\":").append(s.masteredCount).append(',')
            sb.append("\"knownWordsCount\":").append(s.knownWordsCount).append(',')
            sb.append("\"todayReviewed\":").append(s.todayReviewed).append(',')
            sb.append("\"dailyGoal\":").append(s.dailyGoal).append(',')
            sb.append("\"cefrCode\":\"").append(escapeJson(s.cefrCode)).append("\",")
            sb.append("\"lastActiveDateKey\":").append(s.lastActiveDateKey?.let { "\"${escapeJson(it)}\"" } ?: "null")
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decodeProfileStats(json: String?): Map<String, ProfileStatsSnapshot> {
        if (json.isNullOrBlank()) return emptyMap()
        val parsed = runCatching { JsonReader(json).parseValue() as? List<*> }.getOrNull() ?: return emptyMap()
        return parsed.mapNotNull { raw ->
            val obj = raw as? Map<*, *> ?: return@mapNotNull null
            val profileId = obj["profileId"] as? String ?: return@mapNotNull null
            profileId to ProfileStatsSnapshot(
                profileId = profileId,
                currentStreakDays = (obj["currentStreakDays"] as? Number)?.toInt() ?: 0,
                longestStreakDays = (obj["longestStreakDays"] as? Number)?.toInt() ?: 0,
                totalXp = (obj["totalXp"] as? Number)?.toInt() ?: 0,
                masteredCount = (obj["masteredCount"] as? Number)?.toInt() ?: 0,
                knownWordsCount = (obj["knownWordsCount"] as? Number)?.toInt() ?: 0,
                todayReviewed = (obj["todayReviewed"] as? Number)?.toInt() ?: 0,
                dailyGoal = (obj["dailyGoal"] as? Number)?.toInt() ?: 10,
                cefrCode = (obj["cefrCode"] as? String) ?: "A0",
                lastActiveDateKey = obj["lastActiveDateKey"] as? String,
            )
        }.toMap()
    }

    private fun parseLearnerProfile(obj: Map<*, *>?): LearnerProfile? {
        if (obj == null) return null
        val id = (obj["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = (obj["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: LearnerProfile.DEFAULT_NAME
        val avatar = (obj["avatar"] as? String)?.takeIf { it.isNotBlank() } ?: LearnerProfile.DEFAULT_AVATAR
        val color = ((obj["color"] ?: obj["colorHex"]) as? String)?.takeIf { it.isNotBlank() } ?: LearnerProfile.DEFAULT_COLOR
        val kind = ProfileKind.fromCode(obj["kind"] as? String)
        val createdAt = (obj["createdAt"] as? Number)?.toLong() ?: 0L
        return LearnerProfile(
            id = id,
            name = name,
            avatar = avatar,
            colorHex = color,
            kind = kind,
            createdAtMs = createdAt,
        )
    }

    fun decode(json: String): List<FavoriteEntity> = decodeFull(json).favorites

    fun decodeFull(json: String): FullBackupData {
        val parsed = JsonReader(json).parseValue()
        if (parsed is List<*>) {
            return FullBackupData(parseFavorites(parsed), emptyList(), emptyList(), null)
        }
        val obj = parsed as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object or array")
        if (obj["profiles"] is List<*>) {
            val multi = decodeProfiles(json)
            val entry = multi.profiles.firstOrNull { it.profile.id == multi.activeProfileId }
                ?: multi.profiles.firstOrNull()
                ?: return FullBackupData(emptyList(), emptyList(), emptyList(), null)
            val placement = entry.settings.placementLevel?.let { lvl ->
                PlacementBackup(lvl, entry.settings.placementCeilingFreq, entry.settings.placementBaselineWords)
            }
            return FullBackupData(entry.favorites, entry.flashcards, entry.dailyLogs, placement)
        }
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

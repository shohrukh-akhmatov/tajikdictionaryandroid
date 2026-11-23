package tj.tajikdictionary.data.user

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.domain.model.CefrLevel
import tj.tajikdictionary.domain.model.DailyStudySnapshot
import tj.tajikdictionary.domain.model.FlashcardScheduler
import tj.tajikdictionary.domain.model.FlashcardState
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.LexiconStats
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot
import tj.tajikdictionary.domain.model.ReviewRating
import tj.tajikdictionary.domain.model.WordCardCandidate
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.domain.model.WordProgressSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Favorites + history + flashcard & game progress on top of [UserDatabase].
 * Supports switching the active learner's [UserDatabase] at runtime so all observed flows
 * emit the active profile's isolated learning store immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserDataRepository(
    initialDb: UserDatabase,
    private val dbFactory: (String) -> UserDatabase = { initialDb },
) {

    private val writeMutex = Mutex()
    private val dbsByProfile = linkedMapOf(LearnerProfile.DEFAULT_PROFILE_ID to initialDb)
    private val activeDbFlow = MutableStateFlow(initialDb)
    private val db: UserDatabase get() = activeDbFlow.value

    val favorites: Flow<List<FavoriteEntity>> = activeDbFlow.flatMapLatest { it.favorites().observeAll() }
    val history: Flow<List<HistoryEntity>> = activeDbFlow.flatMapLatest { it.history().observeRecent(HistoryDao.MAX_HISTORY) }
    val flashcards: Flow<List<FlashcardEntity>> = activeDbFlow.flatMapLatest { it.flashcards().observeAll() }
    val dailyStats: Flow<List<DailyStatsEntity>> = activeDbFlow.flatMapLatest { it.dailyStats().observeAll() }

    fun isFavorite(entryId: Long): Flow<Boolean> = activeDbFlow.flatMapLatest { it.favorites().observeIsFavorite(entryId) }

    fun flashcard(entryId: Long): Flow<FlashcardEntity?> = activeDbFlow.flatMapLatest { it.flashcards().observeOne(entryId) }

    fun databaseForProfile(profileId: String): UserDatabase = synchronized(dbsByProfile) {
        dbsByProfile.getOrPut(profileId) { dbFactory(profileId) }
    }

    fun switchProfile(profileId: String) {
        val target = databaseForProfile(profileId)
        if (activeDbFlow.value !== target) {
            activeDbFlow.value = target
        }
    }

    suspend fun deleteProfileData(context: Context?, profileId: String) = writeMutex.withLock {
        if (profileId == LearnerProfile.DEFAULT_PROFILE_ID) {
            val defaultDb = databaseForProfile(LearnerProfile.DEFAULT_PROFILE_ID)
            defaultDb.favorites().clearAll()
            defaultDb.history().clear()
            defaultDb.flashcards().clearAll()
            defaultDb.dailyStats().clearAll()
        } else {
            val removed = synchronized(dbsByProfile) { dbsByProfile.remove(profileId) }
            runCatching { removed?.close() }
            if (context != null) {
                UserDatabase.deleteForProfile(context, profileId)
            }
        }
    }

    suspend fun setFavorite(entryId: Long, language: Language, headword: String, preview: String, favorite: Boolean) {
        if (favorite) {
            db.favorites().upsert(FavoriteEntity(entryId, language.code, headword, preview, System.currentTimeMillis()))
        } else {
            db.favorites().delete(entryId)
        }
    }

    suspend fun removeFavorite(entryId: Long) = db.favorites().delete(entryId)

    /** Upserts imported favorites; returns number of records written. */
    suspend fun importFavorites(items: List<FavoriteEntity>): Int {
        items.forEach { db.favorites().upsert(it) }
        return items.size
    }

    /** Records a viewed entry; re-viewing moves it to the top. */
    suspend fun recordView(entryId: Long, language: Language, headword: String, preview: String) {
        db.history().upsert(HistoryEntity(entryId, language.code, headword, preview, System.currentTimeMillis()))
        db.history().trim()
    }

    suspend fun removeFromHistory(entryId: Long) = db.history().delete(entryId)

    suspend fun clearHistory() = db.history().clear()

    suspend fun reviewFlashcard(
        entryId: Long,
        language: Language,
        headword: String,
        preview: String,
        rating: ReviewRating,
        responseMs: Long = 3_000L,
    ) = writeMutex.withLock {
        val now = System.currentTimeMillis()
        val existing = db.flashcards().get(entryId)
        val currentState = existing?.let {
            FlashcardState(it.repetitions, it.intervalDays, it.easeFactor)
        } ?: FlashcardState()
        val (nextState, nextDue) = FlashcardScheduler.next(currentState, rating, now)
        val (eval, newlyLearned) = WordGameEngine.evaluateClassicFlashcard(
            existing = existing?.toSnapshot(),
            entryId = entryId,
            rating = rating,
            intervalDays = nextState.intervalDays,
            nextDueMs = nextDue,
            nowMs = now,
        )
        db.flashcards().upsert(
            FlashcardEntity(
                entryId = entryId,
                lang = language.code,
                headword = headword,
                preview = preview,
                repetitions = nextState.repetitions,
                intervalDays = nextState.intervalDays,
                easeFactor = nextState.easeFactor,
                nextReviewAt = nextDue,
                masteryStage = eval.updated.masteryStage,
                stabilityDays = eval.updated.stabilityDays,
                lapses = eval.updated.lapses,
                lastReviewedAt = now,
                isKnownSkipped = false,
            ),
        )
        recordDailyActivityLocked(
            nowMs = now,
            wordsReviewedDelta = 1,
            wordsLearnedDelta = if (newlyLearned) 1 else 0,
            xpDelta = eval.xpAwarded,
            studySecondsDelta = min(60, max(1, ((responseMs + 500L) / 1000L).toInt())),
            correctDelta = if (rating != ReviewRating.AGAIN) 1 else 0,
        )
    }

    suspend fun recordGameReview(
        card: WordCardCandidate,
        isCorrect: Boolean,
        usedHintOrNearMiss: Boolean,
        responseMs: Long,
        wasRetriedInRound: Boolean,
    ): Pair<Int, Boolean> = writeMutex.withLock {
        val now = System.currentTimeMillis()
        val existing = db.flashcards().get(card.entryId)
        val snapshot = existing?.toSnapshot()
        val eval = WordGameEngine.evaluateAnswer(
            existing = snapshot,
            entryId = card.entryId,
            isCorrect = isCorrect,
            usedHintOrNearMiss = usedHintOrNearMiss,
            responseMs = responseMs,
            wasRetriedInRound = wasRetriedInRound,
            nowMs = now,
        )

        db.flashcards().upsert(
            FlashcardEntity(
                entryId = card.entryId,
                lang = card.language.code,
                headword = card.headword,
                preview = card.preview,
                repetitions = eval.updated.repetitions,
                intervalDays = max(1, eval.updated.stabilityDays.roundToInt()),
                easeFactor = existing?.easeFactor ?: 2.5,
                nextReviewAt = eval.updated.nextReviewAtMs,
                masteryStage = eval.updated.masteryStage,
                stabilityDays = eval.updated.stabilityDays,
                lapses = eval.updated.lapses,
                lastReviewedAt = eval.updated.lastReviewedAtMs,
                isKnownSkipped = false,
            ),
        )

        val activeSecs = min(60, max(1, ((responseMs + 500L) / 1000L).toInt()))
        recordDailyActivityLocked(
            nowMs = now,
            wordsReviewedDelta = 1,
            wordsLearnedDelta = if (eval.newlyLearned) 1 else 0,
            xpDelta = eval.xpAwarded,
            studySecondsDelta = activeSecs,
            correctDelta = if (isCorrect) 1 else 0,
        )
        eval.xpAwarded to eval.newlyLearned
    }

    suspend fun markWordKnown(card: WordCardCandidate) = writeMutex.withLock {
        markEntryKnownLocked(card.entryId, card.language, card.headword, card.preview)
    }

    suspend fun markEntryKnown(
        entryId: Long,
        language: Language,
        headword: String,
        preview: String,
    ) = writeMutex.withLock {
        markEntryKnownLocked(entryId, language, headword, preview)
    }

    suspend fun queueWordForPractice(
        entryId: Long,
        language: Language,
        headword: String,
        preview: String,
    ) = writeMutex.withLock {
        val now = System.currentTimeMillis()
        val existing = db.flashcards().get(entryId)
        db.flashcards().upsert(
            FlashcardEntity(
                entryId = entryId,
                lang = language.code,
                headword = headword,
                preview = preview,
                repetitions = existing?.repetitions ?: 0,
                intervalDays = 0,
                easeFactor = existing?.easeFactor ?: 2.5,
                nextReviewAt = now,
                masteryStage = 0,
                stabilityDays = 0.25,
                lapses = existing?.lapses ?: 0,
                lastReviewedAt = existing?.lastReviewedAt ?: now,
                isKnownSkipped = false,
            ),
        )
    }

    suspend fun recordRoundCompleted(bonusXp: Int = 20) = writeMutex.withLock {
        recordDailyActivityLocked(
            nowMs = System.currentTimeMillis(),
            xpDelta = bonusXp,
            roundsCompletedDelta = 1,
        )
    }

    suspend fun applyPlacementAnswers(
        knownCards: List<WordCardCandidate>,
        focusCards: List<WordCardCandidate>,
    ) = writeMutex.withLock {
        val now = System.currentTimeMillis()
        for (card in knownCards) {
            markEntryKnownLocked(card.entryId, card.language, card.headword, card.preview)
        }
        for (card in focusCards) {
            val existing = db.flashcards().get(card.entryId)
            db.flashcards().upsert(
                FlashcardEntity(
                    entryId = card.entryId,
                    lang = card.language.code,
                    headword = card.headword,
                    preview = card.preview,
                    repetitions = existing?.repetitions ?: 0,
                    intervalDays = 0,
                    easeFactor = existing?.easeFactor ?: 2.3,
                    nextReviewAt = now,
                    masteryStage = 0,
                    stabilityDays = 0.25,
                    lapses = (existing?.lapses ?: 0) + 1,
                    lastReviewedAt = existing?.lastReviewedAt ?: now,
                    isKnownSkipped = false,
                ),
            )
        }
    }

    suspend fun importFullBackup(backup: FavoritesBackup.FullBackupData): Int = writeMutex.withLock {
        importIntoDbLocked(db, backup.favorites, backup.flashcards, backup.dailyLogs)
    }

    /**
     * Exports all local profiles (up to [LearnerProfile.MAX_PROFILES]) with their settings,
     * saved words, flashcard progress, and daily logs into a cross-platform `ProfileBackup v1` JSON string.
     */
    suspend fun exportAllProfiles(settings: SettingsRepository): String = writeMutex.withLock {
        val profileList = settings.profiles.first()
        val activeId = settings.activeProfileId.first()
        val entries = profileList.map { profile ->
            val targetDb = databaseForProfile(profile.id)
            val profileSettings = settings.getProfileSettingsBackup(profile.id)
            FavoritesBackup.ProfileEntryBackup(
                profile = profile,
                settings = profileSettings,
                favorites = targetDb.favorites().getAll(),
                flashcards = targetDb.flashcards().getAll(),
                dailyLogs = targetDb.dailyStats().getAll(),
            )
        }
        FavoritesBackup.encodeProfiles(entries, activeProfileId = activeId)
    }

    /**
     * Imports either a multi-profile `ProfileBackup v1` JSON or a legacy single-profile backup (v1/v2).
     * Returns total number of favorite + flashcard records imported across all profiles.
     */
    suspend fun importProfilesBackup(
        json: String,
        settings: SettingsRepository,
    ): Int = writeMutex.withLock {
        val trimmed = json.trim()
        val isMultiFormat = trimmed.contains("\"profiles\"")
        if (!isMultiFormat) {
            val decoded = FavoritesBackup.decodeFull(json)
            val count = importIntoDbLocked(db, decoded.favorites, decoded.flashcards, decoded.dailyLogs)
            decoded.placement?.let { p ->
                settings.applyPlacementBackup(p.cefrLevel, p.ceilingFreq, p.baselineWords)
            }
            refreshProfileStatsLocked(settings.activeProfileId.first(), db, settings)
            return@withLock count
        }

        val multi = FavoritesBackup.decodeProfiles(json)
        var totalImported = 0
        for (entry in multi.profiles) {
            val shouldMakeActive = entry.profile.id == multi.activeProfileId
            val targetProfile = settings.upsertImportedProfile(
                incoming = entry.profile,
                settingsBackup = entry.settings,
                makeActive = shouldMakeActive,
            ) ?: continue
            val targetDb = databaseForProfile(targetProfile.id)
            totalImported += importIntoDbLocked(targetDb, entry.favorites, entry.flashcards, entry.dailyLogs)
            refreshProfileStatsLocked(targetProfile.id, targetDb, settings)
        }
        val activeId = settings.activeProfileId.first()
        switchProfile(activeId)
        totalImported
    }

    suspend fun computeAndSaveActiveProfileStats(settings: SettingsRepository) = writeMutex.withLock {
        val activeId = settings.activeProfileId.first()
        refreshProfileStatsLocked(activeId, databaseForProfile(activeId), settings)
    }

    private suspend fun refreshProfileStatsLocked(
        profileId: String,
        targetDb: UserDatabase,
        settings: SettingsRepository,
    ) {
        val cards = targetDb.flashcards().getAll()
        val logs = targetDb.dailyStats().getAll()
        val cfg = settings.getProfileSettingsBackup(profileId)
        val placementLvl = cfg.placementLevel?.let { code ->
            CefrLevel.entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
        }
        val summary = WordGameEngine.computeSummary(
            progress = cards.map { it.toSnapshot() },
            logs = logs.map { it.toSnapshot() },
            dailyGoal = cfg.dailyGoal,
            lexiconStats = LexiconStats.FALLBACK,
            placementLevel = placementLvl,
            placementBaselineWords = cfg.placementBaselineWords,
        )
        val latestDateKey = logs.filter { it.wordsReviewed > 0 }.maxByOrNull { it.dateKey }?.dateKey
        settings.updateProfileStats(
            ProfileStatsSnapshot(
                profileId = profileId,
                currentStreakDays = summary.currentStreakDays,
                longestStreakDays = summary.longestStreakDays,
                totalXp = summary.totalXp,
                masteredCount = summary.masteredCount,
                knownWordsCount = summary.estimatedKnownWords,
                todayReviewed = summary.todayReviewed,
                dailyGoal = summary.dailyGoal,
                cefrCode = summary.cefrLevel.code,
                lastActiveDateKey = latestDateKey,
            ),
        )
    }

    private suspend fun importIntoDbLocked(
        targetDb: UserDatabase,
        favorites: List<FavoriteEntity>,
        flashcards: List<FlashcardEntity>,
        dailyLogs: List<DailyStatsEntity>,
    ): Int {
        favorites.forEach { targetDb.favorites().upsert(it) }
        for (card in flashcards) {
            val existing = targetDb.flashcards().get(card.entryId)
            val incomingLast = card.lastReviewedAt ?: 0L
            val existingLast = existing?.lastReviewedAt ?: 0L
            if (existing == null || incomingLast > existingLast || card.masteryStage >= existing.masteryStage) {
                targetDb.flashcards().upsert(UserDataRepository.mergeFlashcards(card, existing))
            }
        }
        for (log in dailyLogs) {
            val existing = targetDb.dailyStats().get(log.dateKey)
            if (existing == null) {
                targetDb.dailyStats().upsert(log)
            } else {
                targetDb.dailyStats().upsert(
                    DailyStatsEntity(
                        dateKey = log.dateKey,
                        wordsReviewed = max(existing.wordsReviewed, log.wordsReviewed),
                        wordsLearned = max(existing.wordsLearned, log.wordsLearned),
                        xpEarned = max(existing.xpEarned, log.xpEarned),
                        roundsCompleted = max(existing.roundsCompleted, log.roundsCompleted),
                        studySeconds = max(existing.studySeconds, log.studySeconds),
                        correctReviews = max(existing.correctReviews, log.correctReviews),
                    ),
                )
            }
        }
        return favorites.size + flashcards.size
    }

    /**
     * Re-links stored Favorites, History, and Flashcards by `(lang, headword)` whenever the bundled
     * `dictionary.sqlite` `data_version` changes so user records survive dictionary rebuilds.
     */
    suspend fun relinkIfNeeded(
        dictionary: DictionaryRepository,
        prefs: SharedPreferences? = null,
    ): Int = writeMutex.withLock {
        val dataVersion = dictionary.info().dataVersion
        if (prefs != null && dataVersion.isNotEmpty() && prefs.getString(PREF_RELINKED_DATA_VERSION, null) == dataVersion) {
            return@withLock 0
        }
        val relinked = relinkAllLocked(
            lookupEntry = { id -> dictionary.entry(id)?.let { it.language.code to it.headword } },
            resolveId = { lang, hw -> dictionary.resolveEntryId(lang, hw) },
        )
        if (prefs != null && dataVersion.isNotEmpty()) {
            prefs.edit().putString(PREF_RELINKED_DATA_VERSION, dataVersion).apply()
        }
        relinked
    }

    internal suspend fun relinkAllLocked(
        lookupEntry: suspend (Long) -> Pair<String, String>?,
        resolveId: suspend (String, String) -> Long?,
    ): Int {
        var relinkedCount = 0

        for (fav in db.favorites().getAll()) {
            val current = lookupEntry(fav.entryId)
            if (current != null && current.first == fav.lang && current.second.equals(fav.headword, ignoreCase = true)) {
                continue
            }
            val newId = resolveId(fav.lang, fav.headword) ?: continue
            val targetEntry = lookupEntry(newId)
            val newHeadword = targetEntry?.second ?: fav.headword
            if (newId != fav.entryId) {
                val existingTarget = db.favorites().get(newId)
                val merged = mergeFavorites(fav.copy(entryId = newId, headword = newHeadword), existingTarget)
                db.favorites().delete(fav.entryId)
                db.favorites().upsert(merged)
                relinkedCount++
            } else if (newHeadword != fav.headword) {
                db.favorites().upsert(fav.copy(headword = newHeadword))
                relinkedCount++
            }
        }

        for (hist in db.history().getAll()) {
            val current = lookupEntry(hist.entryId)
            if (current != null && current.first == hist.lang && current.second.equals(hist.headword, ignoreCase = true)) {
                continue
            }
            val newId = resolveId(hist.lang, hist.headword) ?: continue
            val targetEntry = lookupEntry(newId)
            val newHeadword = targetEntry?.second ?: hist.headword
            if (newId != hist.entryId) {
                val existingTarget = db.history().get(newId)
                val merged = mergeHistory(hist.copy(entryId = newId, headword = newHeadword), existingTarget)
                db.history().delete(hist.entryId)
                db.history().upsert(merged)
                relinkedCount++
            } else if (newHeadword != hist.headword) {
                db.history().upsert(hist.copy(headword = newHeadword))
                relinkedCount++
            }
        }

        for (card in db.flashcards().getAll()) {
            val current = lookupEntry(card.entryId)
            if (current != null && current.first == card.lang && current.second.equals(card.headword, ignoreCase = true)) {
                continue
            }
            val newId = resolveId(card.lang, card.headword) ?: continue
            val targetEntry = lookupEntry(newId)
            val newHeadword = targetEntry?.second ?: card.headword
            if (newId != card.entryId) {
                val existingTarget = db.flashcards().get(newId)
                val merged = mergeFlashcards(card.copy(entryId = newId, headword = newHeadword), existingTarget)
                db.flashcards().delete(card.entryId)
                db.flashcards().upsert(merged)
                relinkedCount++
            } else if (newHeadword != card.headword) {
                db.flashcards().upsert(card.copy(headword = newHeadword))
                relinkedCount++
            }
        }

        return relinkedCount
    }

    companion object {
        private const val PREF_RELINKED_DATA_VERSION = "relinked_data_version"

        internal fun mergeFavorites(incoming: FavoriteEntity, existing: FavoriteEntity?): FavoriteEntity =
            if (existing == null) incoming else existing.copy(addedAt = min(existing.addedAt, incoming.addedAt))

        internal fun mergeHistory(incoming: HistoryEntity, existing: HistoryEntity?): HistoryEntity =
            if (existing == null) incoming else existing.copy(viewedAt = max(existing.viewedAt, incoming.viewedAt))

        internal fun mergeFlashcards(incoming: FlashcardEntity, existing: FlashcardEntity?): FlashcardEntity {
            if (existing == null) return incoming
            val winner = when {
                incoming.masteryStage > existing.masteryStage -> incoming
                existing.masteryStage > incoming.masteryStage -> existing
                incoming.repetitions >= existing.repetitions -> incoming
                else -> existing
            }
            return winner.copy(
                repetitions = max(incoming.repetitions, existing.repetitions),
                intervalDays = max(incoming.intervalDays, existing.intervalDays),
                masteryStage = max(incoming.masteryStage, existing.masteryStage),
                stabilityDays = max(incoming.stabilityDays, existing.stabilityDays),
                lapses = max(incoming.lapses, existing.lapses),
                isKnownSkipped = incoming.isKnownSkipped || existing.isKnownSkipped,
            )
        }
    }

    private suspend fun markEntryKnownLocked(
        entryId: Long,
        language: Language,
        headword: String,
        preview: String,
    ) {
        val now = System.currentTimeMillis()
        val future = now + 180L * WordGameEngine.DAY_MS
        val existing = db.flashcards().get(entryId)
        db.flashcards().upsert(
            FlashcardEntity(
                entryId = entryId,
                lang = language.code,
                headword = headword,
                preview = preview,
                repetitions = max(1, existing?.repetitions ?: 1),
                intervalDays = 180,
                easeFactor = existing?.easeFactor ?: 2.5,
                nextReviewAt = future,
                masteryStage = 4,
                stabilityDays = 180.0,
                lapses = existing?.lapses ?: 0,
                lastReviewedAt = now,
                isKnownSkipped = true,
            ),
        )
    }

    private suspend fun recordDailyActivityLocked(
        nowMs: Long,
        wordsReviewedDelta: Int = 0,
        wordsLearnedDelta: Int = 0,
        xpDelta: Int = 0,
        roundsCompletedDelta: Int = 0,
        studySecondsDelta: Int = 0,
        correctDelta: Int = 0,
    ) {
        val dateKey = WordGameEngine.dateKey(nowMs)
        val today = db.dailyStats().get(dateKey)
        db.dailyStats().upsert(
            DailyStatsEntity(
                dateKey = dateKey,
                wordsReviewed = (today?.wordsReviewed ?: 0) + wordsReviewedDelta,
                wordsLearned = (today?.wordsLearned ?: 0) + wordsLearnedDelta,
                xpEarned = (today?.xpEarned ?: 0) + xpDelta,
                roundsCompleted = (today?.roundsCompleted ?: 0) + roundsCompletedDelta,
                studySeconds = (today?.studySeconds ?: 0) + studySecondsDelta,
                correctReviews = (today?.correctReviews ?: 0) + correctDelta,
            ),
        )
    }
}

fun FlashcardEntity.toSnapshot() = WordProgressSnapshot(
    entryId = entryId,
    masteryStage = masteryStage,
    stabilityDays = stabilityDays,
    repetitions = repetitions,
    lapses = lapses,
    lastReviewedAtMs = lastReviewedAt,
    nextReviewAtMs = nextReviewAt,
    isKnownSkipped = isKnownSkipped,
)

fun DailyStatsEntity.toSnapshot() = DailyStudySnapshot(
    dateKey = dateKey,
    wordsReviewed = wordsReviewed,
    wordsLearned = wordsLearned,
    xpEarned = xpEarned,
    roundsCompleted = roundsCompleted,
    studySeconds = studySeconds,
    correctReviews = correctReviews,
)

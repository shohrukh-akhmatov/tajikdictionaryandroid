package tj.tajikdictionary.data.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tj.tajikdictionary.domain.model.DailyStudySnapshot
import tj.tajikdictionary.domain.model.FlashcardScheduler
import tj.tajikdictionary.domain.model.FlashcardState
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.ReviewRating
import tj.tajikdictionary.domain.model.WordCardCandidate
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.domain.model.WordProgressSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Favorites + history + flashcard & game progress on top of [UserDatabase]. */
class UserDataRepository(private val db: UserDatabase) {

    private val writeMutex = Mutex()

    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val history: Flow<List<HistoryEntity>> = db.history().observeRecent(HistoryDao.MAX_HISTORY)
    val flashcards: Flow<List<FlashcardEntity>> = db.flashcards().observeAll()
    val dailyStats: Flow<List<DailyStatsEntity>> = db.dailyStats().observeAll()

    fun isFavorite(entryId: Long): Flow<Boolean> = db.favorites().observeIsFavorite(entryId)

    fun flashcard(entryId: Long): Flow<FlashcardEntity?> = db.flashcards().observeOne(entryId)

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
        backup.favorites.forEach { db.favorites().upsert(it) }
        for (card in backup.flashcards) {
            val existing = db.flashcards().get(card.entryId)
            if (existing == null || card.masteryStage >= existing.masteryStage) {
                db.flashcards().upsert(card)
            }
        }
        for (log in backup.dailyLogs) {
            val existing = db.dailyStats().get(log.dateKey)
            if (existing == null) {
                db.dailyStats().upsert(log)
            } else {
                db.dailyStats().upsert(
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
        backup.favorites.size + backup.flashcards.size
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

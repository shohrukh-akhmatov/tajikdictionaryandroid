package tj.tajikdictionary.data.user

import kotlinx.coroutines.flow.Flow
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

    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val history: Flow<List<HistoryEntity>> = db.history().observeRecent(HistoryDao.MAX_HISTORY)
    val flashcards: Flow<List<FlashcardEntity>> = db.flashcards().observeAll()
    val dailyStats: Flow<List<DailyStatsEntity>> = db.dailyStats().observeAll()

    fun isFavorite(entryId: Long): Flow<Boolean> = db.favorites().observeIsFavorite(entryId)

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
    ) {
        val now = System.currentTimeMillis()
        val existing = db.flashcards().get(entryId)
        val currentState = existing?.let {
            FlashcardState(it.repetitions, it.intervalDays, it.easeFactor)
        } ?: FlashcardState()
        val (nextState, nextDue) = FlashcardScheduler.next(currentState, rating, now)
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
                masteryStage = existing?.masteryStage ?: if (rating == ReviewRating.AGAIN) 0 else 1,
                stabilityDays = existing?.stabilityDays ?: nextState.intervalDays.toDouble(),
                lapses = existing?.lapses ?: if (rating == ReviewRating.AGAIN) 1 else 0,
                lastReviewedAt = now,
                isKnownSkipped = existing?.isKnownSkipped ?: false,
            ),
        )
    }

    suspend fun recordGameReview(
        card: WordCardCandidate,
        isCorrect: Boolean,
        usedHintOrNearMiss: Boolean,
        responseMs: Long,
        wasRetriedInRound: Boolean,
    ): Pair<Int, Boolean> {
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

        val dateKey = WordGameEngine.dateKey(now)
        val today = db.dailyStats().get(dateKey)
        val activeSecs = min(60, max(1, ((responseMs + 500L) / 1000L).toInt()))
        db.dailyStats().upsert(
            DailyStatsEntity(
                dateKey = dateKey,
                wordsReviewed = (today?.wordsReviewed ?: 0) + 1,
                wordsLearned = (today?.wordsLearned ?: 0) + if (eval.newlyLearned) 1 else 0,
                xpEarned = (today?.xpEarned ?: 0) + eval.xpAwarded,
                roundsCompleted = today?.roundsCompleted ?: 0,
                studySeconds = (today?.studySeconds ?: 0) + activeSecs,
                correctReviews = (today?.correctReviews ?: 0) + if (isCorrect) 1 else 0,
            ),
        )
        return eval.xpAwarded to eval.newlyLearned
    }

    suspend fun markWordKnown(card: WordCardCandidate) {
        val now = System.currentTimeMillis()
        val future = now + 180L * WordGameEngine.DAY_MS
        val existing = db.flashcards().get(card.entryId)
        db.flashcards().upsert(
            FlashcardEntity(
                entryId = card.entryId,
                lang = card.language.code,
                headword = card.headword,
                preview = card.preview,
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

    suspend fun recordRoundCompleted(bonusXp: Int = 20) {
        val dateKey = WordGameEngine.dateKey()
        val today = db.dailyStats().get(dateKey)
        db.dailyStats().upsert(
            DailyStatsEntity(
                dateKey = dateKey,
                wordsReviewed = today?.wordsReviewed ?: 0,
                wordsLearned = today?.wordsLearned ?: 0,
                xpEarned = (today?.xpEarned ?: 0) + bonusXp,
                roundsCompleted = (today?.roundsCompleted ?: 0) + 1,
                studySeconds = today?.studySeconds ?: 0,
                correctReviews = today?.correctReviews ?: 0,
            ),
        )
    }

    suspend fun applyPlacementAnswers(
        knownCards: List<WordCardCandidate>,
        focusCards: List<WordCardCandidate>,
    ) {
        val now = System.currentTimeMillis()
        for (card in knownCards) {
            markWordKnown(card)
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

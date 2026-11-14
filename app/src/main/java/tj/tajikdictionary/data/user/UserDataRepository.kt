package tj.tajikdictionary.data.user

import kotlinx.coroutines.flow.Flow
import tj.tajikdictionary.domain.model.FlashcardScheduler
import tj.tajikdictionary.domain.model.FlashcardState
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.ReviewRating

/** Favorites + history + flashcard progress on top of [UserDatabase]. */
class UserDataRepository(private val db: UserDatabase) {

    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val history: Flow<List<HistoryEntity>> = db.history().observeRecent(HistoryDao.MAX_HISTORY)
    val flashcards: Flow<List<FlashcardEntity>> = db.flashcards().observeAll()

    fun isFavorite(entryId: Long): Flow<Boolean> = db.favorites().observeIsFavorite(entryId)

    suspend fun setFavorite(entryId: Long, language: Language, headword: String, preview: String, favorite: Boolean) {
        if (favorite) {
            db.favorites().upsert(FavoriteEntity(entryId, language.code, headword, preview, System.currentTimeMillis()))
        } else {
            db.favorites().delete(entryId)
        }
    }

    suspend fun removeFavorite(entryId: Long) = db.favorites().delete(entryId)

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
            ),
        )
    }
}

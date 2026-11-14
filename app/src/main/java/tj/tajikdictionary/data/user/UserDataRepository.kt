package tj.tajikdictionary.data.user

import kotlinx.coroutines.flow.Flow
import tj.tajikdictionary.domain.model.Language

/** Favorites + history on top of [UserDatabase]. */
class UserDataRepository(private val db: UserDatabase) {

    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val history: Flow<List<HistoryEntity>> = db.history().observeRecent(HistoryDao.MAX_HISTORY)

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
}

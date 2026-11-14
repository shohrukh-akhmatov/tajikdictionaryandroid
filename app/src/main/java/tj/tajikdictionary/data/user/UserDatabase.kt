package tj.tajikdictionary.data.user

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * User data (favorites, history). Kept separate from the read-only dictionary so it survives
 * dictionary updates. `lang + headword` are stored alongside `entryId` so items can be re-linked
 * if entry IDs change between data versions.
 */
@Entity(tableName = "favorites", indices = [Index(value = ["lang", "headword"])])
data class FavoriteEntity(
    @PrimaryKey val entryId: Long,
    val lang: String,
    val headword: String,
    val preview: String,
    val addedAt: Long,
)

@Entity(tableName = "history", indices = [Index(value = ["viewedAt"])])
data class HistoryEntity(
    @PrimaryKey val entryId: Long,
    val lang: String,
    val headword: String,
    val preview: String,
    val viewedAt: Long,
)

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE entryId = :entryId)")
    fun observeIsFavorite(entryId: Long): Flow<Boolean>

    @Upsert
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE entryId = :entryId")
    suspend fun delete(entryId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY viewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE entryId = :entryId")
    suspend fun delete(entryId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keeps only the newest [MAX_HISTORY] rows. */
    @Query("DELETE FROM history WHERE entryId NOT IN (SELECT entryId FROM history ORDER BY viewedAt DESC LIMIT $MAX_HISTORY)")
    suspend fun trim()

    companion object {
        const val MAX_HISTORY = 200
    }
}

@Database(entities = [FavoriteEntity::class, HistoryEntity::class], version = 1, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun favorites(): FavoritesDao
    abstract fun history(): HistoryDao

    companion object {
        fun create(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "user.db").build()
    }
}

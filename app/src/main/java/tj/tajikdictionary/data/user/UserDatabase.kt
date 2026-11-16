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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * User data (favorites, history, flashcards). Kept separate from the read-only dictionary so it
 * survives dictionary updates. `lang + headword` are stored alongside `entryId` so items can be
 * re-linked if entry IDs change between data versions.
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

@Entity(tableName = "flashcards", indices = [Index(value = ["nextReviewAt"])])
data class FlashcardEntity(
    @PrimaryKey val entryId: Long,
    val lang: String,
    val headword: String,
    val preview: String,
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val nextReviewAt: Long,
    val masteryStage: Int = 0,
    val stabilityDays: Double = 0.0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
    val isKnownSkipped: Boolean = false,
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val dateKey: String,
    val wordsReviewed: Int,
    val wordsLearned: Int,
    val xpEarned: Int,
    val roundsCompleted: Int,
    val studySeconds: Int = 0,
    val correctReviews: Int = 0,
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

@Dao
interface FlashcardsDao {
    @Query("SELECT * FROM flashcards ORDER BY nextReviewAt ASC")
    fun observeAll(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE entryId = :entryId")
    suspend fun get(entryId: Long): FlashcardEntity?

    @Query("SELECT * FROM flashcards WHERE entryId = :entryId")
    fun observeOne(entryId: Long): Flow<FlashcardEntity?>

    @Upsert
    suspend fun upsert(item: FlashcardEntity)
}

@Dao
interface DailyStatsDao {
    @Query("SELECT * FROM daily_stats ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<DailyStatsEntity>>

    @Query("SELECT * FROM daily_stats WHERE dateKey = :dateKey")
    suspend fun get(dateKey: String): DailyStatsEntity?

    @Upsert
    suspend fun upsert(item: DailyStatsEntity)
}

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, FlashcardEntity::class, DailyStatsEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun favorites(): FavoritesDao
    abstract fun history(): HistoryDao
    abstract fun flashcards(): FlashcardsDao
    abstract fun dailyStats(): DailyStatsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `flashcards` (
                        `entryId` INTEGER NOT NULL,
                        `lang` TEXT NOT NULL,
                        `headword` TEXT NOT NULL,
                        `preview` TEXT NOT NULL,
                        `repetitions` INTEGER NOT NULL,
                        `intervalDays` INTEGER NOT NULL,
                        `easeFactor` REAL NOT NULL,
                        `nextReviewAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entryId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_nextReviewAt` ON `flashcards` (`nextReviewAt`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `masteryStage` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `stabilityDays` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `lapses` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `lastReviewedAt` INTEGER")
                db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `isKnownSkipped` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_stats` (
                        `dateKey` TEXT NOT NULL,
                        `wordsReviewed` INTEGER NOT NULL,
                        `wordsLearned` INTEGER NOT NULL,
                        `xpEarned` INTEGER NOT NULL,
                        `roundsCompleted` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_stats` ADD COLUMN `studySeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `daily_stats` ADD COLUMN `correctReviews` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "user.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

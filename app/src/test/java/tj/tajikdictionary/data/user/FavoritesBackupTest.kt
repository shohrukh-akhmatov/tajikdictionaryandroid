package tj.tajikdictionary.data.user

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesBackupTest {
    @Test
    fun roundTripPreservesTajikRussianAndEscapedCharacters() {
        val sample = listOf(
            FavoriteEntity(
                entryId = 42L,
                lang = "tg",
                headword = "китоб",
                preview = "книга; book \"reading\"\nline2",
                addedAt = 1_731_000_000_000L,
            ),
            FavoriteEntity(
                entryId = 108L,
                lang = "ru",
                headword = "здравствуйте",
                preview = "салом; hello",
                addedAt = 1_731_000_001_000L,
            ),
        )
        val json = FavoritesBackup.encode(sample, exportedAtMs = 1_731_000_002_000L)
        val decoded = FavoritesBackup.decode(json)
        assertEquals(sample, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedJson() {
        FavoritesBackup.decode("{broken json")
    }

    @Test
    fun fullBackupV2RoundTripAndV1BackwardCompat() {
        val favs = listOf(
            FavoriteEntity(42L, "tg", "китоб", "book", 1_731_000_000_000L),
        )
        val cards = listOf(
            FlashcardEntity(
                entryId = 42L,
                lang = "tg",
                headword = "китоб",
                preview = "book",
                repetitions = 3,
                intervalDays = 6,
                easeFactor = 2.6,
                nextReviewAt = 1_731_500_000_000L,
                masteryStage = 3,
                stabilityDays = 6.5,
                lapses = 1,
                lastReviewedAt = 1_731_000_000_000L,
                isKnownSkipped = false,
            ),
        )
        val logs = listOf(
            DailyStatsEntity("2026-11-15", 12, 3, 140, 1, 240, 10),
        )
        val placement = FavoritesBackup.PlacementBackup("B1", 60, 1800)

        val v2Json = FavoritesBackup.encodeFull(favs, cards, logs, placement, exportedAtMs = 1_731_000_005_000L)
        val decodedV2 = FavoritesBackup.decodeFull(v2Json)
        assertEquals(favs, decodedV2.favorites)
        assertEquals(cards, decodedV2.flashcards)
        assertEquals(logs, decodedV2.dailyLogs)
        assertEquals(placement, decodedV2.placement)

        val v1Json = FavoritesBackup.encode(favs, exportedAtMs = 1_731_000_005_000L)
        val decodedV1 = FavoritesBackup.decodeFull(v1Json)
        assertEquals(favs, decodedV1.favorites)
        assertEquals(emptyList<FlashcardEntity>(), decodedV1.flashcards)
        assertEquals(emptyList<DailyStatsEntity>(), decodedV1.dailyLogs)
        assertEquals(null, decodedV1.placement)
    }
}

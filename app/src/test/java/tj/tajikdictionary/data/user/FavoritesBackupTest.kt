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

    @Test
    fun multiProfileBackupV1RoundTripAndLegacyFallback() {
        val p1 = tj.tajikdictionary.domain.model.LearnerProfile.defaultProfile(name = "Parent", createdAtMs = 1_731_000_000_000L)
        val p2 = tj.tajikdictionary.domain.model.LearnerProfile(
            id = "p_child1",
            name = "Zarina",
            avatar = "🦊",
            colorHex = "#E65100",
            kind = tj.tajikdictionary.domain.model.ProfileKind.CHILD,
            createdAtMs = 1_731_000_100_000L,
        )
        val entries = listOf(
            FavoritesBackup.ProfileEntryBackup(
                profile = p1,
                settings = FavoritesBackup.ProfileSettingsBackup(
                    studyMode = "TJ_RU",
                    dailyGoal = 15,
                    placementLevel = "A2",
                    placementCeilingFreq = 82,
                    placementBaselineWords = 500,
                ),
                favorites = listOf(FavoriteEntity(42L, "tg", "китоб", "книга", 1_731_000_000_000L)),
                flashcards = listOf(
                    FlashcardEntity(
                        entryId = 42L,
                        lang = "tg",
                        headword = "китоб",
                        preview = "книга",
                        repetitions = 4,
                        intervalDays = 10,
                        easeFactor = 2.5,
                        nextReviewAt = 1_731_900_000_000L,
                        masteryStage = 3,
                        stabilityDays = 10.0,
                        lapses = 0,
                        lastReviewedAt = 1_731_000_000_000L,
                        isKnownSkipped = false,
                    ),
                ),
                dailyLogs = listOf(DailyStatsEntity("2026-11-22", 15, 5, 180, 2, 300, 14)),
            ),
            FavoritesBackup.ProfileEntryBackup(
                profile = p2,
                settings = FavoritesBackup.ProfileSettingsBackup(
                    studyMode = "TJ_EN",
                    dailyGoal = 5,
                    placementLevel = null,
                    placementCeilingFreq = 100,
                    placementBaselineWords = 0,
                ),
                favorites = emptyList(),
                flashcards = emptyList(),
                dailyLogs = listOf(DailyStatsEntity("2026-11-22", 6, 3, 80, 1, 120, 6)),
            ),
        )

        val json = FavoritesBackup.encodeProfiles(
            profiles = entries,
            activeProfileId = "p_child1",
            exportedAtMs = 1_731_000_200_000L,
        )
        val decoded = FavoritesBackup.decodeProfiles(json)
        assertEquals(FavoritesBackup.PROFILE_BACKUP_FORMAT, decoded.format)
        assertEquals(1, decoded.version)
        assertEquals("p_child1", decoded.activeProfileId)
        assertEquals(2, decoded.profiles.size)
        assertEquals("Parent", decoded.profiles[0].profile.name)
        assertEquals("A2", decoded.profiles[0].settings.placementLevel)
        assertEquals(1, decoded.profiles[0].favorites.size)
        assertEquals(1, decoded.profiles[0].flashcards.size)
        assertEquals("Zarina", decoded.profiles[1].profile.name)
        assertEquals(tj.tajikdictionary.domain.model.ProfileKind.CHILD, decoded.profiles[1].profile.kind)
        assertEquals(5, decoded.profiles[1].settings.dailyGoal)
    }
}

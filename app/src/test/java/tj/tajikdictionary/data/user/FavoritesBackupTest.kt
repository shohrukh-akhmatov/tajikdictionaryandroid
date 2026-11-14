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
}

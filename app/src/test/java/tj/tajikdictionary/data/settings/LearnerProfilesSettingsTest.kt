package tj.tajikdictionary.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.tajikdictionary.domain.model.CefrLevel
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.PlacementResult
import tj.tajikdictionary.domain.model.ProfileKind
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot

class LearnerProfilesSettingsTest {

    private val settings = SettingsRepository(InMemoryPreferences())

    @Test
    fun startsWithSingleDefaultProfile() = runBlocking {
        val profiles = settings.profiles.first()
        assertEquals(1, profiles.size)
        assertEquals(LearnerProfile.DEFAULT_PROFILE_ID, profiles.first().id)
        assertEquals(LearnerProfile.DEFAULT_PROFILE_ID, settings.activeProfileId.first())
    }

    @Test
    fun enforcesMaxThreeProfilesLimitPerPhone() = runBlocking {
        // Default profile is #1; we can add #2 and #3, and #4 must be rejected (null).
        val p2 = settings.addProfile(name = "Zarina", avatar = "🦁", colorHex = "#1E6F9F", kind = ProfileKind.CHILD)
        assertNotNull(p2)
        assertEquals(2, settings.profiles.first().size)
        assertEquals(p2!!.id, settings.activeProfileId.first())
        assertEquals(5, settings.dailyGoal.first()) // Child default daily goal is 5

        val p3 = settings.addProfile(name = "Amir", avatar = "🚀", colorHex = "#7B1FA2", kind = ProfileKind.ADULT)
        assertNotNull(p3)
        assertEquals(3, settings.profiles.first().size)
        assertEquals(p3!!.id, settings.activeProfileId.first())
        assertEquals(10, settings.dailyGoal.first()) // Adult default daily goal is 10

        // 4th profile exceeds MAX_PROFILES (3) and must be rejected.
        val p4 = settings.addProfile(name = "Extra", avatar = "🐼", colorHex = "#E65100", kind = ProfileKind.ADULT)
        assertNull(p4)
        assertEquals(3, settings.profiles.first().size)
    }

    @Test
    fun perProfileLearningSettingsAreIsolatedAcrossLearners() = runBlocking {
        // Configure default profile #1
        settings.setStudyMode(DictionaryMode.TJ_RU)
        settings.setDailyGoal(15)
        settings.applyPlacement(
            PlacementResult(
                cefrLevel = CefrLevel.B1,
                estimatedKnownWords = 1800,
                textRecognitionPercent = 78.0,
                correctCount = 12,
                totalCount = 16,
                knownEntryIds = emptyList(),
                focusEntryIds = emptyList(),
            ),
        )

        // Add child profile #2 and switch to it
        val child = settings.addProfile(name = "Zarina", avatar = "🦋", colorHex = "#E65100", kind = ProfileKind.CHILD)!!
        assertEquals(child.id, settings.activeProfileId.first())
        assertEquals(5, settings.dailyGoal.first())
        assertFalse(settings.hasCompletedPlacement.first())
        assertNull(settings.placementLevel.first())

        // Change child's studyMode to TJ_EN
        settings.setStudyMode(DictionaryMode.TJ_EN)
        assertEquals(DictionaryMode.TJ_EN, settings.studyMode.first())

        // Switch back to default profile #1: its settings must be intact
        settings.switchProfile(LearnerProfile.DEFAULT_PROFILE_ID)
        assertEquals(DictionaryMode.TJ_RU, settings.studyMode.first())
        assertEquals(15, settings.dailyGoal.first())
        assertTrue(settings.hasCompletedPlacement.first())
        assertEquals(CefrLevel.B1, settings.placementLevel.first())
    }

    @Test
    fun updateDeleteAndStatsSnapshotsWorkCorrectly() = runBlocking {
        // Cannot delete the only profile
        assertFalse(settings.deleteProfile(LearnerProfile.DEFAULT_PROFILE_ID))

        val p2 = settings.addProfile(name = "Zarina", avatar = "🦁", colorHex = "#1E6F9F", kind = ProfileKind.CHILD)!!
        settings.updateProfile(id = p2.id, name = "Zarina Jan", avatar = "🦉", colorHex = "#2E7D32", kind = ProfileKind.ADULT)
        val updated = settings.activeProfile.first()
        assertEquals("Zarina Jan", updated.name)
        assertEquals("🦉", updated.avatar)
        assertEquals(ProfileKind.ADULT, updated.kind)

        settings.updateProfileStats(
            ProfileStatsSnapshot(
                profileId = p2.id,
                currentStreakDays = 4,
                longestStreakDays = 7,
                totalXp = 320,
                masteredCount = 12,
                knownWordsCount = 450,
                todayReviewed = 10,
                dailyGoal = 10,
                cefrCode = "A2",
                lastActiveDateKey = "2026-11-22",
            ),
        )
        assertEquals(320, settings.profileStats.first()[p2.id]?.totalXp)

        // Deleting active profile p2 switches back to the remaining profile and removes its stats
        assertTrue(settings.deleteProfile(p2.id))
        assertEquals(1, settings.profiles.first().size)
        assertEquals(LearnerProfile.DEFAULT_PROFILE_ID, settings.activeProfileId.first())
        assertNull(settings.profileStats.first()[p2.id])
    }
}

package tj.tajikdictionary.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.tajikdictionary.domain.model.DictionaryMode

/** Onboarding shows only on a fresh install; anyone upgrading with saved settings skips it (iOS `SettingsStore`). */
class OnboardingSettingsTest {

    private fun fresh() = SettingsRepository(InMemoryPreferences())

    @Test
    fun freshInstallHasNotCompletedOnboarding() = runBlocking {
        assertFalse(fresh().hasCompletedOnboarding.first())
    }

    @Test
    fun anyLegacySettingCountsAsCompleted() = runBlocking {
        val legacyWrites: List<suspend SettingsRepository.() -> Unit> = listOf(
            { setMode(DictionaryMode.TJ_EN) },
            { setTheme(ThemeSetting.DARK) },
            { setSpeechRate(0.8f) },
            { setFontScale(FontScaleSetting.LARGE) },
            { setDailyGoal(15) },
            { applyPlacementBackup("A2", 70, 500) },
        )
        legacyWrites.forEach { write ->
            val settings = fresh()
            settings.write()
            assertTrue(settings.hasCompletedOnboarding.first())
        }
    }

    @Test
    fun studyModeAloneIsNotALegacyMarker() = runBlocking {
        val settings = fresh()
        settings.setStudyMode(DictionaryMode.TJ_EN)
        assertFalse(settings.hasCompletedOnboarding.first())
    }

    @Test
    fun explicitFlagWins() = runBlocking {
        val settings = fresh()
        settings.setDailyGoal(5)
        settings.setHasCompletedOnboarding(false)
        assertFalse(settings.hasCompletedOnboarding.first())
        settings.setHasCompletedOnboarding(true)
        assertTrue(settings.hasCompletedOnboarding.first())
    }
}

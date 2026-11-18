package tj.tajikdictionary.data.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import tj.tajikdictionary.domain.model.DictionaryMode

/** `studyMode` (Learn) is a separate setting from the dictionary/search mode; same rules as iOS `SettingsStore`. */
class StudyModeSettingsTest {

    private val settings = SettingsRepository(InMemoryPreferences())

    @Test
    fun studyModeDefaultsToDictionaryMode() = runBlocking {
        assertEquals(DictionaryMode.TJ_RU, settings.studyMode.first())
        settings.setMode(DictionaryMode.TJ_EN)
        assertEquals(DictionaryMode.TJ_EN, settings.studyMode.first())
    }

    @Test
    fun changingStudyModeLeavesDictionaryModeAlone() = runBlocking {
        settings.setMode(DictionaryMode.TJ_RU)
        settings.setStudyMode(DictionaryMode.TJ_EN)
        assertEquals(DictionaryMode.TJ_EN, settings.studyMode.first())
        assertEquals(DictionaryMode.TJ_RU, settings.mode.first())

        // Once chosen, the study mode no longer follows the dictionary mode.
        settings.setMode(DictionaryMode.TJ_EN)
        settings.setMode(DictionaryMode.TJ_RU)
        assertEquals(DictionaryMode.TJ_EN, settings.studyMode.first())
    }

    @Test
    fun resolveStudyModeFallbacks() {
        assertEquals(DictionaryMode.TJ_EN, SettingsRepository.resolveStudyMode(null, "TJ_EN"))
        assertEquals(DictionaryMode.TJ_RU, SettingsRepository.resolveStudyMode("TJ_RU", "TJ_EN"))
        assertEquals(DictionaryMode.TJ_RU, SettingsRepository.resolveStudyMode("bogus", null))
    }
}

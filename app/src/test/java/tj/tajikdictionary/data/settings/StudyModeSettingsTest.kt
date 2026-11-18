package tj.tajikdictionary.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test
import tj.tajikdictionary.domain.model.DictionaryMode

/** `studyMode` (Learn) is a separate setting from the dictionary/search mode; same rules as iOS `SettingsStore`. */
class StudyModeSettingsTest {

    /** In-memory store (file-backed DataStore renames are flaky on Windows test runners). */
    private class InMemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

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

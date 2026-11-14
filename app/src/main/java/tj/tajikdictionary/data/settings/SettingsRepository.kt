package tj.tajikdictionary.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tj.tajikdictionary.domain.model.DictionaryMode

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App preferences backed by Jetpack DataStore. */
class SettingsRepository(private val context: Context) {

    private val modeKey = stringPreferencesKey("dictionary_mode")
    private val themeKey = stringPreferencesKey("theme")

    val mode: Flow<DictionaryMode> = context.settingsStore.data.map { prefs ->
        prefs[modeKey]?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() } ?: DictionaryMode.TJ_RU
    }

    val theme: Flow<ThemeSetting> = context.settingsStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM
    }

    suspend fun setMode(mode: DictionaryMode) {
        context.settingsStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setTheme(theme: ThemeSetting) {
        context.settingsStore.edit { it[themeKey] = theme.name }
    }
}

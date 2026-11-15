package tj.tajikdictionary.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tj.tajikdictionary.domain.model.CefrLevel
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.PlacementResult

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

enum class FontScaleSetting(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1.0f),
    LARGE(1.2f),
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App preferences backed by Jetpack DataStore. */
class SettingsRepository(private val context: Context) {

    private val modeKey = stringPreferencesKey("dictionary_mode")
    private val themeKey = stringPreferencesKey("theme")
    private val speechRateKey = floatPreferencesKey("speech_rate")
    private val fontScaleKey = stringPreferencesKey("font_scale")
    private val dailyGoalKey = intPreferencesKey("daily_goal")
    private val placementLevelKey = stringPreferencesKey("placement_level")
    private val placementCeilingFreqKey = intPreferencesKey("placement_ceiling_freq")
    private val placementBaselineWordsKey = intPreferencesKey("placement_baseline_words")
    private val hasCompletedPlacementKey = booleanPreferencesKey("has_completed_placement")

    val mode: Flow<DictionaryMode> = context.settingsStore.data.map { prefs ->
        prefs[modeKey]?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() } ?: DictionaryMode.TJ_RU
    }

    val theme: Flow<ThemeSetting> = context.settingsStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM
    }

    val speechRate: Flow<Float> = context.settingsStore.data.map { prefs ->
        prefs[speechRateKey] ?: 1.0f
    }

    val fontScale: Flow<FontScaleSetting> = context.settingsStore.data.map { prefs ->
        prefs[fontScaleKey]?.let { runCatching { FontScaleSetting.valueOf(it) }.getOrNull() }
            ?: FontScaleSetting.MEDIUM
    }

    val dailyGoal: Flow<Int> = context.settingsStore.data.map { prefs ->
        val goal = prefs[dailyGoalKey] ?: 10
        if (goal > 0) goal else 10
    }

    val placementLevel: Flow<CefrLevel?> = context.settingsStore.data.map { prefs ->
        prefs[placementLevelKey]?.let { raw -> runCatching { CefrLevel.valueOf(raw) }.getOrNull() }
    }

    val placementCeilingFreq: Flow<Int> = context.settingsStore.data.map { prefs ->
        val ceiling = prefs[placementCeilingFreqKey] ?: 100
        if (ceiling in 40..100) ceiling else 100
    }

    val placementBaselineWords: Flow<Int> = context.settingsStore.data.map { prefs ->
        prefs[placementBaselineWordsKey] ?: 0
    }

    val hasCompletedPlacement: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[hasCompletedPlacementKey] ?: false
    }

    suspend fun setMode(mode: DictionaryMode) {
        context.settingsStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setTheme(theme: ThemeSetting) {
        context.settingsStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.settingsStore.edit { it[speechRateKey] = rate }
    }

    suspend fun setFontScale(scale: FontScaleSetting) {
        context.settingsStore.edit { it[fontScaleKey] = scale.name }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.settingsStore.edit { it[dailyGoalKey] = goal }
    }

    suspend fun applyPlacement(result: PlacementResult) {
        context.settingsStore.edit { prefs ->
            prefs[placementLevelKey] = result.cefrLevel.name
            prefs[placementCeilingFreqKey] = result.cefrLevel.recommendedCeilingFrequency
            prefs[placementBaselineWordsKey] = result.cefrLevel.baselineKnownWords
            prefs[hasCompletedPlacementKey] = true
        }
    }
}

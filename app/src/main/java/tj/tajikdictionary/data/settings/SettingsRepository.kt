package tj.tajikdictionary.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.settingsStore)

    private val modeKey = stringPreferencesKey("dictionary_mode")
    private val studyModeKey = stringPreferencesKey("study_mode")
    private val themeKey = stringPreferencesKey("theme")
    private val speechRateKey = floatPreferencesKey("speech_rate")
    private val fontScaleKey = stringPreferencesKey("font_scale")
    private val dailyGoalKey = intPreferencesKey("daily_goal")
    private val placementLevelKey = stringPreferencesKey("placement_level")
    private val placementCeilingFreqKey = intPreferencesKey("placement_ceiling_freq")
    private val placementBaselineWordsKey = intPreferencesKey("placement_baseline_words")
    private val hasCompletedPlacementKey = booleanPreferencesKey("has_completed_placement")
    private val hasCompletedOnboardingKey = booleanPreferencesKey("has_completed_onboarding")

    /** Keys only an existing install can have written; used to skip onboarding for upgraders. */
    private val legacyUsageMarkers: List<Preferences.Key<*>> = listOf(
        modeKey, themeKey, speechRateKey, fontScaleKey, dailyGoalKey, hasCompletedPlacementKey,
    )

    val mode: Flow<DictionaryMode> = store.data.map { prefs ->
        prefs[modeKey]?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() } ?: DictionaryMode.TJ_RU
    }

    /**
     * Language pair used by the learning features (word game, flashcards, placement test). Separate from [mode],
     * so switching it on the Learn screen never changes Search. Until set, it defaults to English.
     * Progress is keyed by the Tajik entry, so it is shared across both learning languages.
     */
    val studyMode: Flow<DictionaryMode> = store.data.map { prefs ->
        resolveStudyMode(prefs[studyModeKey], prefs[modeKey])
    }

    val theme: Flow<ThemeSetting> = store.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM
    }

    val speechRate: Flow<Float> = store.data.map { prefs ->
        prefs[speechRateKey] ?: 1.0f
    }

    val fontScale: Flow<FontScaleSetting> = store.data.map { prefs ->
        prefs[fontScaleKey]?.let { runCatching { FontScaleSetting.valueOf(it) }.getOrNull() }
            ?: FontScaleSetting.MEDIUM
    }

    val dailyGoal: Flow<Int> = store.data.map { prefs ->
        val goal = prefs[dailyGoalKey] ?: 10
        if (goal > 0) goal else 10
    }

    val placementLevel: Flow<CefrLevel?> = store.data.map { prefs ->
        prefs[placementLevelKey]?.let { raw -> runCatching { CefrLevel.valueOf(raw) }.getOrNull() }
    }

    val placementCeilingFreq: Flow<Int> = store.data.map { prefs ->
        val ceiling = prefs[placementCeilingFreqKey] ?: 100
        if (ceiling in 40..100) ceiling else 100
    }

    val placementBaselineWords: Flow<Int> = store.data.map { prefs ->
        prefs[placementBaselineWordsKey] ?: 0
    }

    val hasCompletedPlacement: Flow<Boolean> = store.data.map { prefs ->
        prefs[hasCompletedPlacementKey] ?: false
    }

    /**
     * Whether the first-launch onboarding was finished or skipped. Never set on installs that predate
     * onboarding, so any setting they already saved counts as "done": existing users never see it.
     */
    val hasCompletedOnboarding: Flow<Boolean> = store.data.map { prefs ->
        prefs[hasCompletedOnboardingKey] ?: legacyUsageMarkers.any { prefs.contains(it) }
    }

    suspend fun setMode(mode: DictionaryMode) {
        store.edit { it[modeKey] = mode.name }
    }

    suspend fun setStudyMode(mode: DictionaryMode) {
        store.edit { it[studyModeKey] = mode.name }
    }

    suspend fun setTheme(theme: ThemeSetting) {
        store.edit { it[themeKey] = theme.name }
    }

    suspend fun setSpeechRate(rate: Float) {
        store.edit { it[speechRateKey] = rate }
    }

    suspend fun setFontScale(scale: FontScaleSetting) {
        store.edit { it[fontScaleKey] = scale.name }
    }

    suspend fun setDailyGoal(goal: Int) {
        store.edit { it[dailyGoalKey] = goal }
    }

    suspend fun setHasCompletedOnboarding(done: Boolean) {
        store.edit { it[hasCompletedOnboardingKey] = done }
    }

    suspend fun applyPlacement(result: PlacementResult) {
        store.edit { prefs ->
            prefs[placementLevelKey] = result.cefrLevel.name
            prefs[placementCeilingFreqKey] = result.cefrLevel.recommendedCeilingFrequency
            prefs[placementBaselineWordsKey] = result.cefrLevel.baselineKnownWords
            prefs[hasCompletedPlacementKey] = true
        }
    }

    suspend fun applyPlacementBackup(cefrCode: String, ceilingFreq: Int, baselineWords: Int) {
        val level = CefrLevel.entries.firstOrNull { it.code.equals(cefrCode, ignoreCase = true) || it.name.equals(cefrCode, ignoreCase = true) }
            ?: return
        store.edit { prefs ->
            prefs[placementLevelKey] = level.name
            prefs[placementCeilingFreqKey] = ceilingFreq
            prefs[placementBaselineWordsKey] = baselineWords
            prefs[hasCompletedPlacementKey] = true
        }
    }

    internal companion object {
        fun parseMode(raw: String?): DictionaryMode? = raw?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() }

        /** Saved study mode, else the dictionary mode the user already picked, else TJ_RU. */
        fun resolveStudyMode(studyRaw: String?, dictionaryRaw: String?): DictionaryMode =
            parseMode(studyRaw) ?: DictionaryMode.TJ_EN
    }
}

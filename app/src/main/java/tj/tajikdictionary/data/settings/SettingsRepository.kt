package tj.tajikdictionary.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tj.tajikdictionary.data.user.FavoritesBackup
import tj.tajikdictionary.domain.model.CefrLevel
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.PlacementResult
import tj.tajikdictionary.domain.model.ProfileKind
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot
import java.util.UUID

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

enum class FontScaleSetting(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1.0f),
    LARGE(1.2f),
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App preferences and multi-learner profile registry backed by Jetpack DataStore. */
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

    // Multi-user profile registry keys
    private val profilesJsonKey = stringPreferencesKey("profiles_json")
    private val activeProfileIdKey = stringPreferencesKey("active_profile_id")
    private val profileStatsJsonKey = stringPreferencesKey("profile_stats_json")
    private val askWhoIsLearningKey = booleanPreferencesKey("ask_who_is_learning")
    private val lastProfileSwitchMsKey = longPreferencesKey("last_profile_switch_ms")

    /** Keys only an existing install can have written; used to skip onboarding for upgraders. */
    private val legacyUsageMarkers: List<Preferences.Key<*>> = listOf(
        modeKey, themeKey, speechRateKey, fontScaleKey, dailyGoalKey, hasCompletedPlacementKey,
    )

    private fun studyModeKeyFor(profileId: String) = stringPreferencesKey("study_mode_$profileId")
    private fun dailyGoalKeyFor(profileId: String) = intPreferencesKey("daily_goal_$profileId")
    private fun placementLevelKeyFor(profileId: String) = stringPreferencesKey("placement_level_$profileId")
    private fun placementCeilingFreqKeyFor(profileId: String) = intPreferencesKey("placement_ceiling_freq_$profileId")
    private fun placementBaselineWordsKeyFor(profileId: String) = intPreferencesKey("placement_baseline_words_$profileId")
    private fun hasCompletedPlacementKeyFor(profileId: String) = booleanPreferencesKey("has_completed_placement_$profileId")

    private fun resolveActiveProfile(prefs: Preferences): LearnerProfile {
        val list = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
        val activeId = prefs[activeProfileIdKey]
        return list.firstOrNull { it.id == activeId } ?: list.first()
    }

    val profiles: Flow<List<LearnerProfile>> = store.data.map { prefs ->
        FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
    }

    val activeProfile: Flow<LearnerProfile> = store.data.map { prefs ->
        resolveActiveProfile(prefs)
    }

    val activeProfileId: Flow<String> = activeProfile.map { it.id }

    val profileStats: Flow<Map<String, ProfileStatsSnapshot>> = store.data.map { prefs ->
        FavoritesBackup.decodeProfileStats(prefs[profileStatsJsonKey])
    }

    val askWhoIsLearning: Flow<Boolean> = store.data.map { prefs ->
        prefs[askWhoIsLearningKey] ?: true
    }

    val lastProfileSwitchMs: Flow<Long> = store.data.map { prefs ->
        prefs[lastProfileSwitchMsKey] ?: 0L
    }

    val mode: Flow<DictionaryMode> = store.data.map { prefs ->
        prefs[modeKey]?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() } ?: DictionaryMode.TJ_RU
    }

    /**
     * Per-profile language pair used by the learning features (word game, flashcards, placement test).
     * Separate from [mode], so each learner on the device can study with Russian or English independently.
     */
    val studyMode: Flow<DictionaryMode> = store.data.map { prefs ->
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[studyModeKeyFor(profile.id)]
        val raw = if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[studyModeKey]
        } else {
            scoped
        }
        resolveStudyMode(raw, prefs[modeKey])
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
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[dailyGoalKeyFor(profile.id)]
        val goal = if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[dailyGoalKey] ?: profile.kind.defaultDailyGoal
        } else {
            scoped ?: profile.kind.defaultDailyGoal
        }
        if (goal > 0) goal else profile.kind.defaultDailyGoal
    }

    val placementLevel: Flow<CefrLevel?> = store.data.map { prefs ->
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[placementLevelKeyFor(profile.id)]
        val raw = if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[placementLevelKey]
        } else {
            scoped
        }
        raw?.let { r -> runCatching { CefrLevel.valueOf(r) }.getOrNull() }
    }

    val placementCeilingFreq: Flow<Int> = store.data.map { prefs ->
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[placementCeilingFreqKeyFor(profile.id)]
        val ceiling = if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[placementCeilingFreqKey] ?: 100
        } else {
            scoped ?: 100
        }
        if (ceiling in 40..100) ceiling else 100
    }

    val placementBaselineWords: Flow<Int> = store.data.map { prefs ->
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[placementBaselineWordsKeyFor(profile.id)]
        if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[placementBaselineWordsKey] ?: 0
        } else {
            scoped ?: 0
        }
    }

    val hasCompletedPlacement: Flow<Boolean> = store.data.map { prefs ->
        val profile = resolveActiveProfile(prefs)
        val scoped = prefs[hasCompletedPlacementKeyFor(profile.id)]
        if (scoped == null && profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[hasCompletedPlacementKey] ?: false
        } else {
            scoped ?: false
        }
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
        store.edit { prefs ->
            val profile = resolveActiveProfile(prefs)
            prefs[studyModeKeyFor(profile.id)] = mode.name
            if (profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
                prefs[studyModeKey] = mode.name
            }
        }
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
        store.edit { prefs ->
            val profile = resolveActiveProfile(prefs)
            prefs[dailyGoalKeyFor(profile.id)] = goal
            if (profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
                prefs[dailyGoalKey] = goal
            }
        }
    }

    suspend fun setHasCompletedOnboarding(done: Boolean) {
        store.edit { it[hasCompletedOnboardingKey] = done }
    }

    suspend fun setAskWhoIsLearning(ask: Boolean) {
        store.edit { it[askWhoIsLearningKey] = ask }
    }

    suspend fun recordProfileConfirmed(nowMs: Long = System.currentTimeMillis()) {
        store.edit { it[lastProfileSwitchMsKey] = nowMs }
    }

    suspend fun applyPlacement(result: PlacementResult) {
        store.edit { prefs ->
            val profile = resolveActiveProfile(prefs)
            prefs[placementLevelKeyFor(profile.id)] = result.cefrLevel.name
            prefs[placementCeilingFreqKeyFor(profile.id)] = result.cefrLevel.recommendedCeilingFrequency
            prefs[placementBaselineWordsKeyFor(profile.id)] = result.cefrLevel.baselineKnownWords
            prefs[hasCompletedPlacementKeyFor(profile.id)] = true
            if (profile.id == LearnerProfile.DEFAULT_PROFILE_ID) {
                prefs[placementLevelKey] = result.cefrLevel.name
                prefs[placementCeilingFreqKey] = result.cefrLevel.recommendedCeilingFrequency
                prefs[placementBaselineWordsKey] = result.cefrLevel.baselineKnownWords
                prefs[hasCompletedPlacementKey] = true
            }
        }
    }

    suspend fun applyPlacementBackup(cefrCode: String, ceilingFreq: Int, baselineWords: Int) {
        val level = CefrLevel.entries.firstOrNull { it.code.equals(cefrCode, ignoreCase = true) || it.name.equals(cefrCode, ignoreCase = true) }
            ?: return
        store.edit { prefs ->
            val profile = resolveActiveProfile(prefs)
            writePlacementForProfile(prefs, profile.id, level, ceilingFreq, baselineWords)
        }
    }

    /**
     * Adds a new local learner profile if the device has fewer than [LearnerProfile.MAX_PROFILES] (3).
     * Returns the created [LearnerProfile], or `null` when the 3-profile limit is already reached.
     */
    suspend fun addProfile(
        name: String,
        avatar: String,
        colorHex: String,
        kind: ProfileKind = ProfileKind.ADULT,
        switchToNew: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): LearnerProfile? {
        val cleanName = name.trim().take(24).ifEmpty { LearnerProfile.DEFAULT_NAME }
        val cleanAvatar = avatar.takeIf { it.isNotBlank() } ?: LearnerProfile.DEFAULT_AVATAR
        val cleanColor = colorHex.takeIf { it.isNotBlank() } ?: LearnerProfile.DEFAULT_COLOR
        var created: LearnerProfile? = null
        store.edit { prefs ->
            val current = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
            if (current.size >= LearnerProfile.MAX_PROFILES) return@edit
            val newId = "p_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val profile = LearnerProfile(
                id = newId,
                name = cleanName,
                avatar = cleanAvatar,
                colorHex = cleanColor,
                kind = kind,
                createdAtMs = nowMs,
            )
            val updated = current + profile
            prefs[profilesJsonKey] = FavoritesBackup.encodeProfileList(updated)
            prefs[dailyGoalKeyFor(newId)] = kind.defaultDailyGoal
            val defaultStudyMode = parseMode(prefs[modeKey]) ?: DictionaryMode.TJ_EN
            prefs[studyModeKeyFor(newId)] = defaultStudyMode.name
            if (switchToNew) {
                prefs[activeProfileIdKey] = newId
                prefs[lastProfileSwitchMsKey] = nowMs
            }
            created = profile
        }
        return created
    }

    suspend fun updateProfile(
        id: String,
        name: String,
        avatar: String,
        colorHex: String,
        kind: ProfileKind,
    ) {
        val cleanName = name.trim().take(24).ifEmpty { LearnerProfile.DEFAULT_NAME }
        store.edit { prefs ->
            val current = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
            val updated = current.map { existing ->
                if (existing.id == id) {
                    existing.copy(
                        name = cleanName,
                        avatar = avatar.ifBlank { existing.avatar },
                        colorHex = colorHex.ifBlank { existing.colorHex },
                        kind = kind,
                    )
                } else {
                    existing
                }
            }
            prefs[profilesJsonKey] = FavoritesBackup.encodeProfileList(updated)
        }
    }

    suspend fun switchProfile(id: String, nowMs: Long = System.currentTimeMillis()) {
        store.edit { prefs ->
            val current = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
            if (current.any { it.id == id }) {
                prefs[activeProfileIdKey] = id
                prefs[lastProfileSwitchMsKey] = nowMs
            }
        }
    }

    /**
     * Deletes the profile with [id] if at least one other profile remains.
     * Returns `true` if deleted, `false` if it was the only profile or was not found.
     */
    suspend fun deleteProfile(id: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        var deleted = false
        store.edit { prefs ->
            val current = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
            if (current.size <= 1 || current.none { it.id == id }) return@edit
            val remaining = current.filterNot { it.id == id }
            prefs[profilesJsonKey] = FavoritesBackup.encodeProfileList(remaining)
            clearScopedKeys(prefs, id)
            val stats = FavoritesBackup.decodeProfileStats(prefs[profileStatsJsonKey]).toMutableMap()
            stats.remove(id)
            prefs[profileStatsJsonKey] = FavoritesBackup.encodeProfileStats(stats)
            if (prefs[activeProfileIdKey] == id || remaining.none { it.id == prefs[activeProfileIdKey] }) {
                prefs[activeProfileIdKey] = remaining.first().id
                prefs[lastProfileSwitchMsKey] = nowMs
            }
            deleted = true
        }
        return deleted
    }

    suspend fun updateProfileStats(snapshot: ProfileStatsSnapshot) {
        store.edit { prefs ->
            val stats = FavoritesBackup.decodeProfileStats(prefs[profileStatsJsonKey]).toMutableMap()
            stats[snapshot.profileId] = snapshot
            prefs[profileStatsJsonKey] = FavoritesBackup.encodeProfileStats(stats)
        }
    }

    suspend fun getProfileSettingsBackup(profileId: String): FavoritesBackup.ProfileSettingsBackup {
        val prefs = store.data.first()
        val list = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey])
        val profile = list.firstOrNull { it.id == profileId } ?: LearnerProfile.defaultProfile()
        val isDef = profileId == LearnerProfile.DEFAULT_PROFILE_ID
        val studyRaw = prefs[studyModeKeyFor(profileId)] ?: if (isDef) prefs[studyModeKey] else null
        val mode = resolveStudyMode(studyRaw, prefs[modeKey])
        val goal = prefs[dailyGoalKeyFor(profileId)]
            ?: (if (isDef) prefs[dailyGoalKey] else null)
            ?: profile.kind.defaultDailyGoal
        val hasPlacement = prefs[hasCompletedPlacementKeyFor(profileId)]
            ?: (if (isDef) prefs[hasCompletedPlacementKey] else null)
            ?: false
        val levelRaw = if (hasPlacement) {
            prefs[placementLevelKeyFor(profileId)] ?: if (isDef) prefs[placementLevelKey] else null
        } else {
            null
        }
        val ceiling = prefs[placementCeilingFreqKeyFor(profileId)]
            ?: (if (isDef) prefs[placementCeilingFreqKey] else null)
            ?: 100
        val baseline = prefs[placementBaselineWordsKeyFor(profileId)]
            ?: (if (isDef) prefs[placementBaselineWordsKey] else null)
            ?: 0
        return FavoritesBackup.ProfileSettingsBackup(
            studyMode = mode.name,
            dailyGoal = if (goal > 0) goal else profile.kind.defaultDailyGoal,
            placementLevel = levelRaw,
            placementCeilingFreq = ceiling,
            placementBaselineWords = baseline,
        )
    }

    /**
     * Merges an imported profile and its settings into the device registry (respecting [LearnerProfile.MAX_PROFILES]).
     * Matches an existing profile by ID or case-insensitive name, or creates a new profile if under the limit.
     */
    suspend fun upsertImportedProfile(
        incoming: LearnerProfile,
        settingsBackup: FavoritesBackup.ProfileSettingsBackup,
        makeActive: Boolean = false,
    ): LearnerProfile? {
        var resolved: LearnerProfile? = null
        store.edit { prefs ->
            val current = FavoritesBackup.decodeProfileList(prefs[profilesJsonKey]).toMutableList()
            val matchIndex = current.indexOfFirst {
                it.id == incoming.id || it.name.equals(incoming.name, ignoreCase = true)
            }
            val target: LearnerProfile = if (matchIndex >= 0) {
                val existing = current[matchIndex]
                val merged = existing.copy(
                    name = incoming.name.ifBlank { existing.name },
                    avatar = incoming.avatar.ifBlank { existing.avatar },
                    colorHex = incoming.colorHex.ifBlank { existing.colorHex },
                    kind = incoming.kind,
                )
                current[matchIndex] = merged
                merged
            } else if (current.size < LearnerProfile.MAX_PROFILES) {
                current += incoming
                incoming
            } else {
                return@edit
            }
            prefs[profilesJsonKey] = FavoritesBackup.encodeProfileList(current)
            val studyMode = parseMode(settingsBackup.studyMode) ?: DictionaryMode.TJ_EN
            prefs[studyModeKeyFor(target.id)] = studyMode.name
            if (target.id == LearnerProfile.DEFAULT_PROFILE_ID) {
                prefs[studyModeKey] = studyMode.name
            }
            val goal = if (settingsBackup.dailyGoal > 0) settingsBackup.dailyGoal else target.kind.defaultDailyGoal
            prefs[dailyGoalKeyFor(target.id)] = goal
            if (target.id == LearnerProfile.DEFAULT_PROFILE_ID) {
                prefs[dailyGoalKey] = goal
            }
            settingsBackup.placementLevel?.let { code ->
                val level = CefrLevel.entries.firstOrNull {
                    it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
                }
                if (level != null) {
                    writePlacementForProfile(
                        prefs = prefs,
                        profileId = target.id,
                        level = level,
                        ceilingFreq = settingsBackup.placementCeilingFreq,
                        baselineWords = settingsBackup.placementBaselineWords,
                    )
                }
            }
            if (makeActive) {
                prefs[activeProfileIdKey] = target.id
            }
            resolved = target
        }
        return resolved
    }

    private fun writePlacementForProfile(
        prefs: MutablePreferences,
        profileId: String,
        level: CefrLevel,
        ceilingFreq: Int,
        baselineWords: Int,
    ) {
        prefs[placementLevelKeyFor(profileId)] = level.name
        prefs[placementCeilingFreqKeyFor(profileId)] = ceilingFreq
        prefs[placementBaselineWordsKeyFor(profileId)] = baselineWords
        prefs[hasCompletedPlacementKeyFor(profileId)] = true
        if (profileId == LearnerProfile.DEFAULT_PROFILE_ID) {
            prefs[placementLevelKey] = level.name
            prefs[placementCeilingFreqKey] = ceilingFreq
            prefs[placementBaselineWordsKey] = baselineWords
            prefs[hasCompletedPlacementKey] = true
        }
    }

    private fun clearScopedKeys(prefs: MutablePreferences, profileId: String) {
        prefs.remove(studyModeKeyFor(profileId))
        prefs.remove(dailyGoalKeyFor(profileId))
        prefs.remove(placementLevelKeyFor(profileId))
        prefs.remove(placementCeilingFreqKeyFor(profileId))
        prefs.remove(placementBaselineWordsKeyFor(profileId))
        prefs.remove(hasCompletedPlacementKeyFor(profileId))
    }

    internal companion object {
        fun parseMode(raw: String?): DictionaryMode? = raw?.let { runCatching { DictionaryMode.valueOf(it) }.getOrNull() }

        /** Saved study mode, else TJ_EN. */
        fun resolveStudyMode(studyRaw: String?, dictionaryRaw: String?): DictionaryMode =
            parseMode(studyRaw) ?: DictionaryMode.TJ_EN
    }
}

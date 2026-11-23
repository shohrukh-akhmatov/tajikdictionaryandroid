package tj.tajikdictionary

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tj.tajikdictionary.data.dictionary.DictionaryDatabase
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.data.user.UserDatabase
import tj.tajikdictionary.data.user.toSnapshot
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.LexiconStats
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot
import tj.tajikdictionary.domain.model.WordGameEngine

/** Manual dependency container — manages shared dictionary + multi-learner profile session. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dictionary: DictionaryRepository by lazy { DictionaryRepository(DictionaryDatabase(appContext)) }
    val userData: UserDataRepository by lazy {
        UserDataRepository(
            initialDb = UserDatabase.create(appContext, LearnerProfile.DEFAULT_PROFILE_ID),
            dbFactory = { profileId -> UserDatabase.create(appContext, profileId) },
        )
    }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    init {
        appScope.launch {
            settings.activeProfileId.collectLatest { profileId ->
                userData.switchProfile(profileId)
                runCatching {
                    val prefs = appContext.getSharedPreferences("user_data_relink_$profileId", Context.MODE_PRIVATE)
                    userData.relinkIfNeeded(dictionary, prefs)
                }
            }
        }
        appScope.launch {
            val lexicon = runCatching { dictionary.lexiconStats() }.getOrDefault(LexiconStats.FALLBACK)
            combine(
                settings.activeProfileId,
                userData.flashcards,
                userData.dailyStats,
                settings.dailyGoal,
                combine(settings.placementLevel, settings.placementBaselineWords, ::Pair),
            ) { profileId, cards, logs, goal, (placementLvl, baseline) ->
                val summary = WordGameEngine.computeSummary(
                    progress = cards.map { it.toSnapshot() },
                    logs = logs.map { it.toSnapshot() },
                    dailyGoal = goal,
                    lexiconStats = lexicon,
                    placementLevel = placementLvl,
                    placementBaselineWords = baseline,
                )
                val latestDateKey = logs.filter { it.wordsReviewed > 0 }.maxByOrNull { it.dateKey }?.dateKey
                ProfileStatsSnapshot(
                    profileId = profileId,
                    currentStreakDays = summary.currentStreakDays,
                    longestStreakDays = summary.longestStreakDays,
                    totalXp = summary.totalXp,
                    masteredCount = summary.masteredCount,
                    knownWordsCount = summary.estimatedKnownWords,
                    todayReviewed = summary.todayReviewed,
                    dailyGoal = summary.dailyGoal,
                    cefrCode = summary.cefrLevel.code,
                    lastActiveDateKey = latestDateKey,
                )
            }.collectLatest { snapshot ->
                runCatching { settings.updateProfileStats(snapshot) }
            }
        }
    }

    suspend fun deleteLearnerProfile(profileId: String): Boolean {
        val deleted = settings.deleteProfile(profileId)
        if (deleted) {
            userData.deleteProfileData(appContext, profileId)
        }
        return deleted
    }
}

class TajikDictionaryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

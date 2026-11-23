package tj.tajikdictionary.ui.study

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import tj.tajikdictionary.ui.share.ShareCardButton
import tj.tajikdictionary.ui.share.ShareCardsContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import androidx.activity.compose.LocalActivity
import tj.tajikdictionary.ads.AdsManager
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.FontScaleSetting
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.data.user.toSnapshot
import tj.tajikdictionary.domain.model.AnswerCheckResult
import tj.tajikdictionary.domain.model.ChallengeKind
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.GameRoundItem
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.LexiconStats
import tj.tajikdictionary.domain.model.WordCardCandidate
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.ui.components.EmptyState
import tj.tajikdictionary.ui.components.TajikLettersRow
import tj.tajikdictionary.ui.components.insertAtCaret
import tj.tajikdictionary.ui.components.keyboardDismissExempt
import tj.tajikdictionary.ui.components.localizedLabel
import tj.tajikdictionary.ui.components.localizedTopicName
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface GameDeckSource {
    data object SmartMix : GameDeckSource
    data object DueReviews : GameDeckSource
    data object WeakWords : GameDeckSource
    data object Saved : GameDeckSource
    data class Topic(val topic: String) : GameDeckSource
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordGameScreen(
    source: GameDeckSource,
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onBack: () -> Unit,
) {
    val mode by settings.studyMode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_EN)
    val speechRate by settings.speechRate.collectAsStateWithLifecycle(initialValue = 1.0f)
    val fontScale by settings.fontScale.collectAsStateWithLifecycle(initialValue = FontScaleSetting.MEDIUM)
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val placementLevel by settings.placementLevel.collectAsStateWithLifecycle(initialValue = null)
    val placementCeilingFreq by settings.placementCeilingFreq.collectAsStateWithLifecycle(initialValue = 100)
    val placementBaselineWords by settings.placementBaselineWords.collectAsStateWithLifecycle(initialValue = 0)
    val hasCompletedPlacement by settings.hasCompletedPlacement.collectAsStateWithLifecycle(initialValue = false)
    val activeProfile by settings.activeProfile.collectAsStateWithLifecycle(initialValue = LearnerProfile.defaultProfile())
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyStats by userData.dailyStats.collectAsStateWithLifecycle(initialValue = emptyList())
    val lexiconStats by produceState(initialValue = LexiconStats.FALLBACK, dictionary) {
        value = dictionary.lexiconStats()
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var queue by remember { mutableStateOf<List<GameRoundItem>>(emptyList()) }
    var initialCount by remember { mutableIntStateOf(0) }
    var graduatedCount by remember { mutableIntStateOf(0) }
    var roundFinished by remember { mutableStateOf(false) }
    var roundVersion by remember { mutableIntStateOf(0) }

    var cardPresentedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var typedField by remember { mutableStateOf(TextFieldValue("")) }
    var selectedTileIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var usedHint by remember { mutableStateOf(false) }
    var nearMissTarget by remember { mutableStateOf<String?>(null) }
    var showingTeachingSheet by remember { mutableStateOf(false) }
    var positiveFeedback by remember { mutableStateOf(false) }

    var comboCount by remember { mutableIntStateOf(0) }
    var sessionXp by remember { mutableIntStateOf(0) }
    var firstTryCorrectCount by remember { mutableIntStateOf(0) }
    var totalAttempts by remember { mutableIntStateOf(0) }
    var newlyLearnedInRound by remember { mutableIntStateOf(0) }

    fun resetCardInputs() {
        typedField = TextFieldValue("")
        selectedTileIndices = emptyList()
        selectedChoice = null
        usedHint = false
        nearMissTarget = null
        showingTeachingSheet = false
        positiveFeedback = false
    }

    LaunchedEffect(source, mode, roundVersion, activeProfile.id, activeProfile.kind) {
        isLoading = true
        roundFinished = false
        graduatedCount = 0
        comboCount = 0
        sessionXp = 0
        firstTryCorrectCount = 0
        totalAttempts = 0
        newlyLearnedInRound = 0
        resetCardInputs()

        val roundSize = activeProfile.kind.roundSize
        val progressEntities = userData.flashcards.first()
        val progressMap = progressEntities.associate { it.entryId to it.toSnapshot() }
        val ceilingFreq = if (hasCompletedPlacement) placementCeilingFreq else 100
        val pool: List<WordCardCandidate> = when (source) {
            GameDeckSource.SmartMix -> {
                val frontierPool = dictionary.learningCandidates(
                    topic = null,
                    mode = mode,
                    maxFrequency = ceilingFreq,
                    limit = 450,
                )
                val activeIds = progressEntities
                    .filter { !it.isKnownSkipped && it.lastReviewedAt != null }
                    .map { it.entryId }
                val activeCandidates = dictionary.learningCandidatesForIds(activeIds, mode)
                val seen = HashSet<Long>()
                val merged = ArrayList<WordCardCandidate>(activeCandidates.size + frontierPool.size)
                for (item in activeCandidates + frontierPool) {
                    if (seen.add(item.entryId)) {
                        merged += item
                    }
                }
                if (merged.size < roundSize && ceilingFreq < 100) {
                    for (item in dictionary.learningCandidates(topic = null, mode = mode, maxFrequency = 100, limit = 200)) {
                        if (seen.add(item.entryId)) {
                            merged += item
                        }
                    }
                }
                merged
            }

            is GameDeckSource.Topic -> dictionary.learningCandidates(topic = source.topic, mode = mode, limit = 450)
            GameDeckSource.Saved -> {
                val favIds = userData.favorites.first().map { it.entryId }
                dictionary.learningCandidatesForIds(favIds, mode)
            }

            GameDeckSource.DueReviews, GameDeckSource.WeakWords -> {
                val studiedIds = progressEntities
                    .filter { !it.isKnownSkipped }
                    .map { it.entryId }
                dictionary.learningCandidatesForIds(studiedIds, mode)
            }
        }
        val distractorPool = if (pool.size >= 8) {
            pool
        } else {
            val extra = dictionary.learningCandidates(topic = null, mode = mode, limit = 150)
            (pool + extra).distinctBy { it.entryId }
        }
        val fallback = distractorPool
            .mapNotNull { WordGameEngine.primaryTargetWord(it.preview).takeIf(String::isNotBlank) }
            .distinct()
        val selected = when (source) {
            GameDeckSource.DueReviews -> WordGameEngine.buildDueRoundQueue(
                candidates = pool,
                progressById = progressMap,
                roundSize = roundSize,
            )

            GameDeckSource.WeakWords -> WordGameEngine.buildWeakRoundQueue(
                candidates = pool,
                progressById = progressMap,
                roundSize = roundSize,
            )

            else -> WordGameEngine.buildRoundQueue(
                candidates = pool,
                progressById = progressMap,
                roundSize = roundSize,
                targetMaxFrequency = if (source == GameDeckSource.SmartMix) ceilingFreq else null,
            )
        }
        val rawItems = WordGameEngine.makeRoundItems(
            selected = selected,
            allPool = distractorPool,
            fallbackDistractors = fallback,
            progressById = progressMap,
        )
        val items = if (activeProfile.isChild) {
            rawItems.map { item ->
                if (item.challengeKind == ChallengeKind.TYPING) {
                    item.copy(challengeKind = ChallengeKind.WORD_BUILDER)
                } else {
                    item
                }
            }
        } else {
            rawItems
        }
        queue = items
        initialCount = items.size
        cardPresentedAtMs = System.currentTimeMillis()
        isLoading = false
    }

    fun speakTarget(word: String, entryId: Long) {
        if (!ttsReady || word.isBlank()) return
        val locale = if (mode.partner == Language.RUSSIAN) {
            Locale.forLanguageTag("ru")
        } else {
            Locale.US
        }
        tts.language = locale
        tts.setSpeechRate(speechRate)
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "game_$entryId")
    }

    fun triggerDontKnow() {
        hideKeyboard()
        val current = queue.firstOrNull() ?: return
        totalAttempts += 1
        comboCount = 0
        showingTeachingSheet = true
        positiveFeedback = false
        speakTarget(current.buildTarget, current.card.entryId)
        val elapsed = max(0L, System.currentTimeMillis() - cardPresentedAtMs)
        scope.launch {
            userData.recordGameReview(
                card = current.card,
                isCorrect = false,
                usedHintOrNearMiss = false,
                responseMs = elapsed,
                wasRetriedInRound = current.hadMistakeInRound,
            )
        }
    }

    fun acknowledgeTeachingAndRequeue() {
        if (queue.isEmpty()) return
        val mutable = queue.toMutableList()
        var item = mutable.removeAt(0)
        val downgradedKind = if (item.challengeKind == ChallengeKind.TYPING) {
            WordGameEngine.challengeKind(1, item.buildTarget)
        } else {
            item.challengeKind
        }
        item = item.copy(
            hadMistakeInRound = true,
            requiredCorrectInRound = 2,
            correctInRound = 0,
            challengeKind = downgradedKind,
        )
        val insertAt = WordGameEngine.reinsertIndex(mutable.size)
        mutable.add(insertAt, item)
        queue = mutable
        resetCardInputs()
        cardPresentedAtMs = System.currentTimeMillis()
    }

    fun completeAnswer(isCorrect: Boolean, usedHintOrNearMiss: Boolean) {
        val current = queue.firstOrNull() ?: return
        val elapsed = max(0L, System.currentTimeMillis() - cardPresentedAtMs)
        totalAttempts += 1
        if (!current.hadMistakeInRound && isCorrect) firstTryCorrectCount += 1
        if (isCorrect) comboCount += 1 else comboCount = 0

        val mutable = queue.toMutableList()
        var item = mutable.removeAt(0)
        if (isCorrect) {
            item = item.copy(correctInRound = item.correctInRound + 1)
        }

        if (item.correctInRound < item.requiredCorrectInRound) {
            val insertAt = WordGameEngine.reinsertIndex(mutable.size)
            mutable.add(insertAt, item)
        } else {
            graduatedCount += 1
        }

        val finishedNow = mutable.isEmpty()
        queue = mutable
        resetCardInputs()
        if (finishedNow) {
            roundFinished = true
            AdsManager.recordFinishedRound()
        } else {
            cardPresentedAtMs = System.currentTimeMillis()
        }

        scope.launch {
            val (xp, newly) = userData.recordGameReview(
                card = item.card,
                isCorrect = isCorrect,
                usedHintOrNearMiss = usedHintOrNearMiss,
                responseMs = elapsed,
                wasRetriedInRound = item.hadMistakeInRound,
            )
            sessionXp += xp
            if (newly) newlyLearnedInRound += 1
            if (finishedNow) {
                sessionXp += 20
                userData.recordRoundCompleted(20)
            }
        }
    }

    fun submitText(input: String, item: GameRoundItem) {
        hideKeyboard() // Check button and the keyboard's Enter both land here
        when (val res = WordGameEngine.checkTypedAnswer(input, item.buildTarget, item.acceptedAnswers)) {
            AnswerCheckResult.Exact -> {
                nearMissTarget = null
                speakTarget(item.buildTarget, item.card.entryId)
                completeAnswer(isCorrect = true, usedHintOrNearMiss = usedHint)
            }
            is AnswerCheckResult.TajikLetterNearMiss -> {
                nearMissTarget = res.target
                usedHint = true
            }
            AnswerCheckResult.Wrong -> {
                nearMissTarget = null
                triggerDontKnow()
            }
        }
    }

    fun markAlreadyKnown(item: GameRoundItem) {
        scope.launch { userData.markWordKnown(item.card) }
        if (queue.isNotEmpty()) {
            val mutable = queue.toMutableList()
            mutable.removeAt(0)
            graduatedCount += 1
            val finishedNow = mutable.isEmpty()
            queue = mutable
            resetCardInputs()
            if (finishedNow) {
                roundFinished = true
                AdsManager.recordFinishedRound()
                scope.launch {
                    sessionXp += 20
                    userData.recordRoundCompleted(20)
                }
            } else {
                cardPresentedAtMs = System.currentTimeMillis()
            }
        }
    }

    val title = when (source) {
        GameDeckSource.SmartMix -> stringResource(R.string.game_smart_mix)
        GameDeckSource.DueReviews -> stringResource(R.string.game_due_reviews_title)
        GameDeckSource.WeakWords -> stringResource(R.string.game_weak_words)
        GameDeckSource.Saved -> stringResource(R.string.tab_saved)
        is GameDeckSource.Topic -> localizedTopicName(source.topic)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { StudyLanguageTitle(title, settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!roundFinished && initialCount > 0) {
                        Row(
                            modifier = Modifier.padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (comboCount >= 2) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6D00),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "${comboCount}×",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF6D00),
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    "+$sessionXp XP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            initialCount == 0 -> {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.game_empty_title),
                    message = stringResource(R.string.game_empty_message),
                    modifier = Modifier.padding(padding),
                )
            }

            roundFinished -> {
                val summary = remember(flashcards, dailyStats, dailyGoal, lexiconStats, placementLevel, placementBaselineWords) {
                    WordGameEngine.computeSummary(
                        progress = flashcards.map { it.toSnapshot() },
                        logs = dailyStats.map { it.toSnapshot() },
                        dailyGoal = dailyGoal,
                        lexiconStats = lexiconStats,
                        placementLevel = placementLevel,
                        placementBaselineWords = placementBaselineWords,
                    )
                }
                val accuracy = if (totalAttempts > 0) {
                    ((firstTryCorrectCount.toDouble() / totalAttempts.toDouble()) * 100.0).roundToInt()
                } else {
                    100
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileAvatarCircle(
                            avatar = activeProfile.avatar,
                            colorHex = activeProfile.colorHex,
                        )
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.profile_round_complete_named, activeProfile.localizedDisplayName()),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.game_round_complete_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryStatTile("+$sessionXp", "XP", Modifier.weight(1f))
                        SummaryStatTile("$accuracy%", stringResource(R.string.game_accuracy), Modifier.weight(1f))
                        SummaryStatTile("${summary.currentStreakDays}d", stringResource(R.string.game_streak), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                "${summary.cefrLevel.code} · ${"%.1f".format(Locale.US, summary.textRecognitionPercent)}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        if (summary.todayReviewed >= dailyGoal) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF2E7D32).copy(alpha = 0.14f),
                            ) {
                                Text(
                                    stringResource(R.string.game_daily_goal_met),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF2E7D32),
                                )
                            }
                        }
                    }
                    if (newlyLearnedInRound > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.game_new_started, newlyLearnedInRound),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { roundVersion += 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.game_play_again)) }
                    Spacer(Modifier.height(8.dp))
                    ShareCardButton(content = { ctx ->
                        ShareCardsContent.round(
                            ctx,
                            xp = sessionXp,
                            accuracy = accuracy,
                            newWords = graduatedCount,
                            streakDays = summary.currentStreakDays,
                            cefr = summary.cefrLevel.code,
                        )
                    })
                    Spacer(Modifier.height(8.dp))
                    val activity = LocalActivity.current
                    OutlinedButton(
                        onClick = {
                            if (activeProfile.isChild) {
                                onBack()
                            } else {
                                AdsManager.showInterstitialIfDue(activity) { onBack() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.game_done)) }
                }
            }

            queue.isEmpty() -> {
                val emptyText = when (source) {
                    GameDeckSource.DueReviews -> stringResource(R.string.game_empty_due)
                    GameDeckSource.WeakWords -> stringResource(R.string.game_empty_weak)
                    else -> stringResource(R.string.game_empty_message)
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = title,
                        message = emptyText,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.game_done))
                    }
                }
            }

            else -> {
                val item = queue.first()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { (graduatedCount.toFloat() / max(1, initialCount).toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.game_learned_progress, graduatedCount, initialCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (queue.size > 1) {
                            Text(
                                stringResource(R.string.game_in_queue, queue.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Prompt Card
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val badgeText = when {
                                        item.hadMistakeInRound -> stringResource(
                                            R.string.game_practice_x,
                                            max(1, item.requiredCorrectInRound - item.correctInRound),
                                        )
                                        item.isNewWord -> stringResource(R.string.game_badge_new)
                                        else -> stringResource(R.string.game_badge_review)
                                    }
                                    val badgeBg = when {
                                        item.hadMistakeInRound -> Color(0xFFFF6D00).copy(alpha = 0.14f)
                                        item.isNewWord -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else -> Color(0xFF2E7D32).copy(alpha = 0.14f)
                                    }
                                    val badgeFg = when {
                                        item.hadMistakeInRound -> Color(0xFFE65100)
                                        item.isNewWord -> MaterialTheme.colorScheme.primary
                                        else -> Color(0xFF2E7D32)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(badgeBg, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(badgeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeFg)
                                    }
                                    item.card.pos?.let {
                                        Text(it.localizedLabel(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (ttsReady && item.buildTarget.isNotBlank() && item.challengeKind != ChallengeKind.MULTIPLE_CHOICE) {
                                    IconButton(
                                        onClick = { speakTarget(item.buildTarget, item.card.entryId) },
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.listen))
                                    }
                                }
                            }

                            val partnerLangLabel = if (mode.partner == Language.ENGLISH) {
                                stringResource(R.string.lang_english)
                            } else {
                                stringResource(R.string.lang_russian)
                            }
                            val baseSize = MaterialTheme.typography.headlineSmall
                            Text(
                                text = item.card.headword,
                                style = baseSize.copy(fontSize = baseSize.fontSize * fontScale.scale),
                                fontWeight = FontWeight.Bold,
                            )
                            val directionLabel = if (item.challengeKind == ChallengeKind.MULTIPLE_CHOICE) {
                                "${stringResource(R.string.lang_tajik)} → $partnerLangLabel"
                            } else {
                                val pattern = item.buildTarget.map { if (it == ' ') "  " else "_" }.joinToString(" ")
                                "${stringResource(R.string.lang_tajik)} → $partnerLangLabel  ·  $pattern (${item.buildTarget.length})"
                            }
                            Text(
                                text = directionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )

                            item.clozeSentence?.let { cloze ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = cloze,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        item.clozeTranslation?.takeIf { it.isNotBlank() }?.let { trans ->
                                            Text(
                                                text = trans,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (positiveFeedback) {
                        Text(
                            stringResource(R.string.game_correct),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                        )
                    }

                    if (showingTeachingSheet) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.game_learn_this_word),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (ttsReady && item.buildTarget.isNotBlank()) {
                                        IconButton(
                                            onClick = { speakTarget(item.buildTarget, item.card.entryId) },
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.listen))
                                        }
                                    }
                                }
                                Text(
                                    item.targetAnswer,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (!item.card.preview.equals(item.targetAnswer, ignoreCase = true) && item.card.preview.isNotBlank()) {
                                    Text(
                                        item.card.preview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    item.card.headword,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                item.card.exampleTg?.takeIf { it.isNotBlank() }?.let { exTg ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            item.card.examplePartner?.takeIf { it.isNotBlank() }?.let { exP ->
                                                Text(exP, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text(exTg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text(
                                    stringResource(R.string.game_requeue_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = ::acknowledgeTeachingAndRequeue,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.game_got_it)) }
                            }
                        }
                    } else {
                        val partnerLangLabel = if (mode.partner == Language.ENGLISH) {
                            stringResource(R.string.lang_english)
                        } else {
                            stringResource(R.string.lang_russian)
                        }
                        when (item.challengeKind) {
                            ChallengeKind.MULTIPLE_CHOICE -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item.choices.forEach { choice ->
                                        val isPicked = selectedChoice == choice
                                        val isRight = choice.equals(item.targetAnswer, ignoreCase = true)
                                        val bgColor = when {
                                            selectedChoice == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            isRight -> Color(0xFF2E7D32).copy(alpha = 0.22f)
                                            isPicked -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                        Surface(
                                            color = bgColor,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = selectedChoice == null) {
                                                    selectedChoice = choice
                                                    if (isRight) {
                                                        positiveFeedback = true
                                                        speakTarget(item.buildTarget, item.card.entryId)
                                                        scope.launch {
                                                            delay(300)
                                                            completeAnswer(isCorrect = true, usedHintOrNearMiss = false)
                                                        }
                                                    } else {
                                                        triggerDontKnow()
                                                    }
                                                },
                                        ) {
                                            Text(
                                                text = choice,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(16.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            ChallengeKind.WORD_BUILDER -> {
                                val built = selectedTileIndices.joinToString("") { item.tiles[it] }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = built.ifEmpty { stringResource(R.string.game_tap_tiles) },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (built.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (selectedTileIndices.isNotEmpty()) {
                                            IconButton(onClick = { selectedTileIndices = selectedTileIndices.dropLast(1) }) {
                                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                                            }
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item.tiles.forEachIndexed { idx, tile ->
                                        val used = idx in selectedTileIndices
                                        FilledTonalButton(
                                            onClick = { if (!used) selectedTileIndices = selectedTileIndices + idx },
                                            enabled = !used,
                                            modifier = Modifier.alpha(if (used) 0.35f else 1f),
                                        ) {
                                            Text(tile, style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }

                                Button(
                                    onClick = { submitText(built, item) },
                                    enabled = selectedTileIndices.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.game_check)) }
                            }

                            ChallengeKind.TYPING -> {
                                OutlinedTextField(
                                    value = typedField,
                                    onValueChange = { typedField = it },
                                    modifier = Modifier.fillMaxWidth().keyboardDismissExempt(),
                                    placeholder = { Text("$partnerLangLabel (${item.buildTarget.length})…") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitText(typedField.text, item) }),
                                )

                                nearMissTarget?.let { target ->
                                    Surface(
                                        color = Color(0xFFFF6D00).copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.game_near_miss, target),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFE65100),
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    speakTarget(item.buildTarget, item.card.entryId)
                                                    completeAnswer(isCorrect = true, usedHintOrNearMiss = true)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text(stringResource(R.string.game_use_target, target)) }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            usedHint = true
                                            val prefix = item.buildTarget.take(2)
                                            if (!typedField.text.lowercase().startsWith(prefix.lowercase())) {
                                                typedField = TextFieldValue(prefix, TextRange(prefix.length))
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.game_hint))
                                    }
                                    Button(
                                        onClick = { submitText(typedField.text, item) },
                                        enabled = typedField.text.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.game_check)) }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = ::triggerDontKnow,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.game_dont_know))
                            }

                            if (item.isNewWord && !activeProfile.isChild) {
                                TextButton(onClick = { markAlreadyKnown(item) }) {
                                    Text(stringResource(R.string.game_already_know))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummaryStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

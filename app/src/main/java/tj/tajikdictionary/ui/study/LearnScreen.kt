package tj.tajikdictionary.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import tj.tajikdictionary.ui.share.ShareCardIconButton
import tj.tajikdictionary.ui.share.ShareCardsContent
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.data.user.toSnapshot
import tj.tajikdictionary.domain.model.LexiconStats
import tj.tajikdictionary.domain.model.TopicCount
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.SectionHeader
import tj.tajikdictionary.ui.components.localizedStudyDuration
import tj.tajikdictionary.ui.components.localizedTopicName
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import tj.tajikdictionary.domain.model.GamificationSummary
import tj.tajikdictionary.domain.model.LearnerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onStartGame: (GameDeckSource) -> Unit,
    onOpenFlashcards: (String?) -> Unit,
    onOpenPlacement: () -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasCompletedPlacement by settings.hasCompletedPlacement.collectAsStateWithLifecycle(initialValue = true)
    val profiles by settings.profiles.collectAsStateWithLifecycle(initialValue = listOf(LearnerProfile.defaultProfile()))
    val activeProfile by settings.activeProfile.collectAsStateWithLifecycle(initialValue = LearnerProfile.defaultProfile())
    val profileStats by settings.profileStats.collectAsStateWithLifecycle(initialValue = emptyMap())
    val askWhoIsLearning by settings.askWhoIsLearning.collectAsStateWithLifecycle(initialValue = true)
    val lastProfileSwitchMs by settings.lastProfileSwitchMs.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())

    val topics by produceState<List<TopicCount>>(initialValue = emptyList(), dictionary) {
        value = dictionary.topics()
    }
    val summary = rememberGamificationSummary(dictionary, userData, settings)
    val levelTitle = levelTitle(summary.level)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedSource by remember { mutableStateOf<GameDeckSource>(GameDeckSource.SmartMix) }
    var showProfileSwitcher by rememberSaveable { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<LearnerProfile?>(null) }
    var whoIsLearningChecked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(profiles.size, askWhoIsLearning, lastProfileSwitchMs) {
        if (!whoIsLearningChecked && profiles.size >= 2) {
            whoIsLearningChecked = true
            val elapsed = System.currentTimeMillis() - lastProfileSwitchMs
            if (askWhoIsLearning && elapsed > LearnerProfile.WHO_IS_LEARNING_COOLDOWN_MS) {
                showProfileSwitcher = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.tab_learn), fontWeight = FontWeight.Bold) },
                actions = {
                    ProfileHeaderChip(
                        activeProfile = activeProfile,
                        profileCount = profiles.size,
                        onClick = { showProfileSwitcher = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Streak & Level Card
            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (summary.currentStreakDays > 0) Color(0xFFFF6D00).copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = if (summary.currentStreakDays > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.game_streak_days, summary.currentStreakDays),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.game_best_streak, summary.longestStreakDays),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        ShareCardIconButton(content = { ctx -> ShareCardsContent.progress(ctx, summary, levelTitle) })

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.game_level_badge, summary.level, levelTitle),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${summary.totalXp} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.game_todays_goal),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${summary.todayReviewed} / ${summary.dailyGoal}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (summary.todayReviewed >= summary.dailyGoal) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { min(summary.todayReviewed, summary.dailyGoal).toFloat() / max(1, summary.dailyGoal).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.game_level_progress),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${summary.xpInCurrentLevel} / ${summary.xpForNextLevel} XP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { summary.xpInCurrentLevel.toFloat() / max(1, summary.xpForNextLevel).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                }
            }

            // Word Trainer Hero Card (primary learning action at top)
            AppCard(
                cornerRadius = 20.dp,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.game_trainer_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (summary.dueNowCount > 0) {
                                    stringResource(R.string.game_due_reviews, summary.dueNowCount)
                                } else {
                                    stringResource(R.string.game_trainer_sub)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (summary.dueNowCount > 0) {
                            Surface(
                                color = Color(0xFFE66A00),
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    "${summary.dueNowCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    StudyLanguagePicker(settings)

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        item(key = "smart_mix") {
                            TrainerDeckChip(
                                selected = selectedSource == GameDeckSource.SmartMix,
                                label = stringResource(R.string.game_smart_mix),
                                onClick = { selectedSource = GameDeckSource.SmartMix },
                            )
                        }
                        if (summary.dueNowCount > 0) {
                            item(key = "due_reviews") {
                                TrainerDeckChip(
                                    selected = selectedSource == GameDeckSource.DueReviews,
                                    label = "${stringResource(R.string.game_due_reviews_chip)} (${summary.dueNowCount})",
                                    onClick = { selectedSource = GameDeckSource.DueReviews },
                                )
                            }
                        }
                        val weakCount = flashcards.count {
                            !it.isKnownSkipped && (it.lapses > 0 || (it.lastReviewedAt != null && it.masteryStage <= 1) || it.stabilityDays < 2.0)
                        }
                        if (weakCount > 0) {
                            item(key = "weak_words") {
                                TrainerDeckChip(
                                    selected = selectedSource == GameDeckSource.WeakWords,
                                    label = "${stringResource(R.string.game_weak_words)} ($weakCount)",
                                    onClick = { selectedSource = GameDeckSource.WeakWords },
                                )
                            }
                        }
                        if (favorites.isNotEmpty()) {
                            item(key = "saved") {
                                TrainerDeckChip(
                                    selected = selectedSource == GameDeckSource.Saved,
                                    label = stringResource(R.string.tab_saved),
                                    onClick = { selectedSource = GameDeckSource.Saved },
                                )
                            }
                        }
                        items(topics, key = { it.topic }) { item ->
                            val topicSource = GameDeckSource.Topic(item.topic)
                            TrainerDeckChip(
                                selected = selectedSource == topicSource,
                                label = localizedTopicName(item.topic),
                                onClick = { selectedSource = topicSource },
                            )
                        }
                    }

                    Button(
                        onClick = { onStartGame(selectedSource) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            stringResource(R.string.game_start_round_n, activeProfile.kind.roundSize),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    val topicName = (selectedSource as? GameDeckSource.Topic)?.topic
                    OutlinedButton(
                        onClick = { onOpenFlashcards(topicName) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.game_classic_flashcards))
                    }
                }
            }

            // Multi-user Family & Learners Board (or 1-tap invite when only 1 profile exists)
            FamilyLearnersCard(
                profiles = profiles,
                activeProfile = activeProfile,
                activeSummary = summary,
                statsByProfile = profileStats,
                onSelectProfile = { targetId ->
                    scope.launch { settings.switchProfile(targetId) }
                },
                onOpenSwitcher = { showProfileSwitcher = true },
                onAddProfile = {
                    if (profiles.size < LearnerProfile.MAX_PROFILES) {
                        showAddProfileDialog = true
                    } else {
                        showProfileSwitcher = true
                    }
                },
            )

            // Shown until the placement test is taken once; afterwards it lives on the Progress screen.
            if (!hasCompletedPlacement) {
                LearnLinkRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.placement_cta_check),
                    subtitle = stringResource(R.string.placement_prompt_sub),
                    onClick = onOpenPlacement,
                )
            }

            val unlocked = summary.badges.count { it.unlocked }
            LearnLinkRow(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.progress_link_title),
                subtitle = "${summary.cefrLevel.code} · ${summary.estimatedKnownWords} ${stringResource(R.string.progress_link_words)} · \uD83C\uDFC5 $unlocked/${summary.badges.size}",
                onClick = onOpenProgress,
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showProfileSwitcher) {
        ProfileSwitcherSheet(
            profiles = profiles,
            activeProfile = activeProfile,
            statsByProfile = profileStats,
            onSelectProfile = { targetId ->
                showProfileSwitcher = false
                scope.launch { settings.switchProfile(targetId) }
            },
            onAddProfileClick = {
                showProfileSwitcher = false
                showAddProfileDialog = true
            },
            onEditProfileClick = { target ->
                showProfileSwitcher = false
                editingProfile = target
            },
            onDismiss = {
                showProfileSwitcher = false
                scope.launch { settings.recordProfileConfirmed() }
            },
        )
    }

    if (showAddProfileDialog) {
        ProfileEditorDialog(
            initialProfile = null,
            existingCount = profiles.size,
            canDelete = false,
            onSave = { name, avatar, colorHex, kind ->
                showAddProfileDialog = false
                scope.launch {
                    settings.addProfile(
                        name = name,
                        avatar = avatar,
                        colorHex = colorHex,
                        kind = kind,
                        switchToNew = true,
                    )
                }
            },
            onDismiss = { showAddProfileDialog = false },
        )
    }

    editingProfile?.let { target ->
        ProfileEditorDialog(
            initialProfile = target,
            existingCount = profiles.size,
            canDelete = profiles.size > 1,
            onSave = { name, avatar, colorHex, kind ->
                editingProfile = null
                scope.launch {
                    settings.updateProfile(
                        id = target.id,
                        name = name,
                        avatar = avatar,
                        colorHex = colorHex,
                        kind = kind,
                    )
                }
            },
            onDelete = {
                editingProfile = null
                scope.launch {
                    val deleted = settings.deleteProfile(target.id)
                    if (deleted) {
                        userData.deleteProfileData(context, target.id)
                    }
                }
            },
            onDismiss = { editingProfile = null },
        )
    }
}

/** Streak, XP and today's goal from the user's progress; shared by the Learn and Progress screens. */
@Composable
internal fun rememberGamificationSummary(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
): GamificationSummary {
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyStats by userData.dailyStats.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val placementLevel by settings.placementLevel.collectAsStateWithLifecycle(initialValue = null)
    val placementBaselineWords by settings.placementBaselineWords.collectAsStateWithLifecycle(initialValue = 0)
    val lexiconStats by produceState(initialValue = LexiconStats.FALLBACK, dictionary) {
        value = dictionary.lexiconStats()
    }
    return remember(flashcards, dailyStats, dailyGoal, lexiconStats, placementLevel, placementBaselineWords) {
        WordGameEngine.computeSummary(
            progress = flashcards.map { it.toSnapshot() },
            logs = dailyStats.map { it.toSnapshot() },
            dailyGoal = dailyGoal,
            lexiconStats = lexiconStats,
            placementLevel = placementLevel,
            placementBaselineWords = placementBaselineWords,
        )
    }
}

@Composable
internal fun levelTitle(level: Int): String = when (level) {
    1 -> stringResource(R.string.level_novice)
    2 -> stringResource(R.string.level_explorer)
    3 -> stringResource(R.string.level_learner)
    4 -> stringResource(R.string.level_speaker)
    5 -> stringResource(R.string.level_scholar)
    else -> stringResource(R.string.level_master)
}

/** Tappable row with an icon, title, subtitle and chevron (placement prompt, progress link). */
@Composable
private fun LearnLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrainerDeckChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        label = { Text(label) },
    )
}

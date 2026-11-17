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
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onStartGame: (GameDeckSource) -> Unit,
    onOpenFlashcards: (String?) -> Unit,
    onOpenPlacement: () -> Unit,
    onOpenVocabulary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyStats by userData.dailyStats.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val placementLevel by settings.placementLevel.collectAsStateWithLifecycle(initialValue = null)
    val placementBaselineWords by settings.placementBaselineWords.collectAsStateWithLifecycle(initialValue = 0)
    val hasCompletedPlacement by settings.hasCompletedPlacement.collectAsStateWithLifecycle(initialValue = false)

    val topics by produceState<List<TopicCount>>(initialValue = emptyList(), dictionary) {
        value = dictionary.topics()
    }
    val lexiconStats by produceState(initialValue = LexiconStats.FALLBACK, dictionary) {
        value = dictionary.lexiconStats()
    }

    var selectedSource by remember { mutableStateOf<GameDeckSource>(GameDeckSource.SmartMix) }

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

    val levelTitle = when (summary.level) {
        1 -> stringResource(R.string.level_novice)
        2 -> stringResource(R.string.level_explorer)
        3 -> stringResource(R.string.level_learner)
        4 -> stringResource(R.string.level_speaker)
        5 -> stringResource(R.string.level_scholar)
        else -> stringResource(R.string.level_master)
    }

    val animatedRecognition by animateFloatAsState(
        targetValue = (summary.textRecognitionPercent.toFloat() / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "recognitionRing",
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.tab_learn), fontWeight = FontWeight.Bold) },
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
                                label = item.topic.replaceFirstChar { it.uppercase() },
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
                            stringResource(R.string.game_start_round),
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

            // Text Recognition % + CEFR Placement Card
            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        Box(
                            modifier = Modifier.size(84.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 9.dp.toPx()
                                drawArc(
                                    color = trackColor,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                                drawArc(
                                    color = primaryColor,
                                    startAngle = -90f,
                                    sweepAngle = animatedRecognition * 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    String.format(Locale.US, "%.0f%%", summary.textRecognitionPercent),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    summary.cefrLevel.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.hero_everyday_recognized),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${summary.cefrLevel.code} · ${cefrTitle(summary.cefrLevel)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.hero_words_of_total, summary.estimatedKnownWords, summary.totalLexiconCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenPlacement,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (hasCompletedPlacement) {
                                stringResource(R.string.placement_cta_retake)
                            } else {
                                stringResource(R.string.placement_cta_check)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

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

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Lv. ${summary.level} · $levelTitle",
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

            // Study Analytics Card
            AppCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.analytics_title),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalyticsTile(
                            value = "${summary.estimatedKnownWords}",
                            label = stringResource(R.string.analytics_words_known),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        AnalyticsTile(
                            value = WordGameEngine.formatStudyDuration(summary.totalStudySeconds),
                            label = stringResource(R.string.analytics_time_studied),
                            icon = Icons.Filled.Schedule,
                            color = Color(0xFF7B1FA2),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalyticsTile(
                            value = "${summary.activeDaysCount}",
                            label = stringResource(R.string.analytics_active_days),
                            icon = Icons.Filled.CalendarMonth,
                            color = Color(0xFFE65100),
                            modifier = Modifier.weight(1f),
                        )
                        AnalyticsTile(
                            value = "${summary.overallAccuracyPercent}%",
                            label = stringResource(R.string.analytics_reviews_count, summary.totalReviews),
                            icon = Icons.Filled.TrackChanges,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.analytics_last_7_days),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val maxCount = max(
                            summary.dailyGoal,
                            summary.recent7Days.maxOfOrNull { it.wordsReviewed } ?: 1,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            summary.recent7Days.forEach { day ->
                                val fraction = max(0.08f, day.wordsReviewed.toFloat() / max(1, maxCount).toFloat())
                                val barColor = when {
                                    day.metGoal -> MaterialTheme.colorScheme.primary
                                    day.wordsReviewed > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    if (day.wordsReviewed > 0) {
                                        Text(
                                            "${day.wordsReviewed}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height((52.dp * fraction))
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(barColor),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        day.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Vocabulary Progress & Frequency Tiers
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
                        Text(
                            stringResource(R.string.game_vocab_progress),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = onOpenVocabulary) {
                            Text(stringResource(R.string.vocab_open_browser))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VocabCountTile(summary.encounteredCount, stringResource(R.string.game_seen), Color(0xFF2459C7), Modifier.weight(1f))
                        VocabCountTile(summary.learningCount, stringResource(R.string.game_learning), Color(0xFFE65100), Modifier.weight(1f))
                        VocabCountTile(summary.masteredCount, stringResource(R.string.game_mastered), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }

                    summary.tierBreakdown.forEach { tier ->
                        val tierTitle = when (tier.id) {
                            "t90" -> stringResource(R.string.tier_90_label)
                            "t80" -> stringResource(R.string.tier_80_label)
                            "t70" -> stringResource(R.string.tier_70_label)
                            else -> stringResource(R.string.tier_40_label)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tierTitle, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${tier.knownCount} / ${tier.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (tier.knownCount.toFloat() / max(1, tier.totalCount).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        }
                    }
                }
            }

            // Achievements
            SectionHeader(title = stringResource(R.string.game_achievements))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.badges.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { badge ->
                            val (titleText, subText, icon) = when (badge.id) {
                                "first_word" -> Triple(R.string.badge_first_word, R.string.badge_first_word_sub, Icons.Outlined.AutoAwesome)
                                "ten_words" -> Triple(R.string.badge_ten_words, R.string.badge_ten_words_sub, Icons.Filled.Eco)
                                "streak_3" -> Triple(R.string.badge_streak_3, R.string.badge_streak_3_sub, Icons.Filled.LocalFireDepartment)
                                "streak_7" -> Triple(R.string.badge_streak_7, R.string.badge_streak_7_sub, Icons.Filled.CalendarMonth)
                                "master_25" -> Triple(R.string.badge_master_25, R.string.badge_master_25_sub, Icons.Filled.EmojiEvents)
                                else -> Triple(R.string.badge_reviews_100, R.string.badge_reviews_100_sub, Icons.Filled.Bolt)
                            }
                            AppCard(
                                modifier = Modifier.weight(1f).alpha(if (badge.unlocked) 1f else 0.58f),
                                cornerRadius = 16.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (badge.unlocked) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Column {
                                        Text(stringResource(titleText), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(stringResource(subText), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
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

@Composable
private fun AnalyticsTile(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = color.copy(alpha = 0.09f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VocabCountTile(count: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

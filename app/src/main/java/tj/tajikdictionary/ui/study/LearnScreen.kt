package tj.tajikdictionary.ui.study

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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_learn)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Lingvist-style Text Recognition % + CEFR Placement Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier.size(82.dp),
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
                                    sweepAngle = (summary.textRecognitionPercent.toFloat() / 100f).coerceIn(0f, 1f) * 360f,
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

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.hero_everyday_recognized),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (summary.currentStreakDays > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(30.dp),
                            )
                            Column {
                                Text(
                                    stringResource(R.string.game_streak_days, summary.currentStreakDays),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
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
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${summary.totalXp} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.game_todays_goal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${summary.todayReviewed} / ${summary.dailyGoal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (summary.todayReviewed >= summary.dailyGoal) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { min(summary.todayReviewed, summary.dailyGoal).toFloat() / summary.dailyGoal.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.game_level_progress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${summary.xpInCurrentLevel} / ${summary.xpForNextLevel} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { summary.xpInCurrentLevel.toFloat() / summary.xpForNextLevel.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Word Trainer Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.game_trainer_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (summary.dueNowCount > 0) {
                                    stringResource(R.string.game_due_reviews, summary.dueNowCount)
                                } else {
                                    stringResource(R.string.game_trainer_sub)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (summary.dueNowCount > 0) {
                            Surface(color = Color(0xFFFF6D00), shape = RoundedCornerShape(999.dp)) {
                                Text(
                                    "${summary.dueNowCount}",
                                    style = MaterialTheme.typography.labelMedium,
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
                            FilterChip(
                                selected = selectedSource == GameDeckSource.SmartMix,
                                onClick = { selectedSource = GameDeckSource.SmartMix },
                                label = { Text(stringResource(R.string.game_smart_mix)) },
                            )
                        }
                        if (summary.dueNowCount > 0) {
                            item(key = "due_reviews") {
                                FilterChip(
                                    selected = selectedSource == GameDeckSource.DueReviews,
                                    onClick = { selectedSource = GameDeckSource.DueReviews },
                                    label = { Text("${stringResource(R.string.game_due_reviews_chip)} (${summary.dueNowCount})") },
                                )
                            }
                        }
                        val weakCount = flashcards.count { !it.isKnownSkipped && (it.lapses > 0 || (it.lastReviewedAt != null && it.masteryStage <= 1) || it.stabilityDays < 2.0) }
                        if (weakCount > 0) {
                            item(key = "weak_words") {
                                FilterChip(
                                    selected = selectedSource == GameDeckSource.WeakWords,
                                    onClick = { selectedSource = GameDeckSource.WeakWords },
                                    label = { Text("${stringResource(R.string.game_weak_words)} ($weakCount)") },
                                )
                            }
                        }
                        if (favorites.isNotEmpty()) {
                            item(key = "saved") {
                                FilterChip(
                                    selected = selectedSource == GameDeckSource.Saved,
                                    onClick = { selectedSource = GameDeckSource.Saved },
                                    label = { Text(stringResource(R.string.tab_saved)) },
                                )
                            }
                        }
                        items(topics, key = { it.topic }) { item ->
                            val topicSource = GameDeckSource.Topic(item.topic)
                            FilterChip(
                                selected = selectedSource == topicSource,
                                onClick = { selectedSource = topicSource },
                                label = { Text(item.topic.replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }

                    Button(
                        onClick = { onStartGame(selectedSource) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.game_start_round))
                    }

                    val topicName = (selectedSource as? GameDeckSource.Topic)?.topic
                    OutlinedButton(
                        onClick = { onOpenFlashcards(topicName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.game_classic_flashcards))
                    }
                }
            }

            // Study Analytics Card (Words known, Study time, Active days, Accuracy + 7-day chart)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.analytics_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
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
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 7-day bar chart
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
                                    day.metGoal -> Color(0xFF2E7D32)
                                    day.wordsReviewed > 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
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
                                            .width(18.dp)
                                            .height((52.dp * fraction))
                                            .clip(RoundedCornerShape(4.dp))
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
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.game_vocab_progress),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
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
                        VocabCountTile(summary.encounteredCount, stringResource(R.string.game_seen), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        VocabCountTile(summary.learningCount, stringResource(R.string.game_learning), Color(0xFFE65100), Modifier.weight(1f))
                        VocabCountTile(summary.masteredCount, stringResource(R.string.game_mastered), Color(0xFF2E7D32), Modifier.weight(1f))
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
                                Text(tierTitle, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "${tier.knownCount} / ${tier.totalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (tier.knownCount.toFloat() / max(1, tier.totalCount).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Achievements
            Text(
                stringResource(R.string.game_achievements),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                            ElevatedCard(
                                modifier = Modifier.weight(1f).alpha(if (badge.unlocked) 1f else 0.55f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
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
        }
    }
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
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
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
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

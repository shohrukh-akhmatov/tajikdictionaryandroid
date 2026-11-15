package tj.tajikdictionary.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.data.user.toSnapshot
import tj.tajikdictionary.domain.model.DailyStudySnapshot
import tj.tajikdictionary.domain.model.TopicCount
import tj.tajikdictionary.domain.model.WordGameEngine
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onStartGame: (GameDeckSource) -> Unit,
    onOpenFlashcards: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyStats by userData.dailyStats.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val topics by produceState<List<TopicCount>>(initialValue = emptyList(), dictionary) {
        value = dictionary.topics()
    }

    var selectedSource by remember { mutableStateOf<GameDeckSource>(GameDeckSource.SmartMix) }

    val summary = remember(flashcards, dailyStats, dailyGoal) {
        WordGameEngine.computeSummary(
            progress = flashcards.map { it.toSnapshot() },
            logs = dailyStats.map {
                DailyStudySnapshot(it.dateKey, it.wordsReviewed, it.wordsLearned, it.xpEarned, it.roundsCompleted)
            },
            dailyGoal = dailyGoal,
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Lv. ${summary.level} · $levelTitle",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
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
                            Text(stringResource(R.string.game_todays_goal), style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${summary.todayReviewed} / ${summary.dailyGoal}",
                                style = MaterialTheme.typography.labelMedium,
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
                            Text(
                                stringResource(R.string.game_level_progress),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${summary.xpInCurrentLevel} / ${summary.xpForNextLevel} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            val target = GameDeckSource.Topic(item.topic)
                            FilterChip(
                                selected = selectedSource == target,
                                onClick = { selectedSource = target },
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

            // Vocabulary Coverage Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.game_vocab_progress),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VocabCountTile(summary.encounteredCount, stringResource(R.string.game_seen), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        VocabCountTile(summary.learningCount, stringResource(R.string.game_learning), Color(0xFFE65100), Modifier.weight(1f))
                        VocabCountTile(summary.masteredCount, stringResource(R.string.game_mastered), Color(0xFF2E7D32), Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.game_lexicon_coverage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${summary.encounteredCount} / 8,041",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (summary.encounteredCount.toFloat() / 8041f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
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

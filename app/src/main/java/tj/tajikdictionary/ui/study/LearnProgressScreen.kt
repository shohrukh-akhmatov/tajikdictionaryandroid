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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
/**
 * Everything about "how am I doing": recognition level, stats, vocabulary tiers and achievements.
 * Split out of the Learn screen so that screen stays focused on starting today's practice (iOS `LearnProgressView`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnProgressScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onBack: () -> Unit,
    onOpenPlacement: () -> Unit,
    onOpenVocabulary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasCompletedPlacement by settings.hasCompletedPlacement.collectAsStateWithLifecycle(initialValue = false)
    val summary = rememberGamificationSummary(dictionary, userData, settings)
    val levelTitle = levelTitle(summary.level)

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
                title = { Text(stringResource(R.string.progress_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    ShareCardIconButton(content = { ctx -> ShareCardsContent.progress(ctx, summary, levelTitle) })
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
                            value = localizedStudyDuration(summary.totalStudySeconds),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(titleText), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text(stringResource(subText), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (badge.unlocked) {
                                        ShareCardIconButton(
                                            content = { ctx ->
                                                ShareCardsContent.badge(ctx, icon, ctx.getString(titleText), ctx.getString(subText), summary)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        )
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

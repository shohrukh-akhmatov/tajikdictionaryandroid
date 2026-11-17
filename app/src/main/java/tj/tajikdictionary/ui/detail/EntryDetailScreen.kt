package tj.tajikdictionary.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.core.text.TajikTransliterator
import tj.tajikdictionary.data.user.FlashcardEntity
import tj.tajikdictionary.domain.model.EntryDetail
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.domain.model.Translation
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.LanguageTag
import tj.tajikdictionary.ui.components.PosBadge
import tj.tajikdictionary.ui.components.SectionHeader
import tj.tajikdictionary.ui.components.TtsSpeaker
import tj.tajikdictionary.ui.components.rememberTtsSpeaker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onTopicClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speaker = rememberTtsSpeaker()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        text = state.detail?.entry?.headword.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.favorite),
                                tint = if (state.isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            detail == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.entry_not_found))
                }
            }

            else -> {
                DetailContent(
                    detail = detail,
                    preferredLanguage = state.mode.translationTarget(detail.entry.language),
                    speaker = speaker,
                    speechRate = state.speechRate,
                    fontScale = state.fontScale.scale,
                    cardProgress = state.cardProgress,
                    onCopy = { copyEntry(context, detail) },
                    onShare = { shareEntry(context, detail) },
                    onQueueForPractice = {
                        viewModel.queueForPractice()
                        Toast.makeText(context, context.getString(R.string.detail_queued_toast), Toast.LENGTH_SHORT).show()
                    },
                    onMarkKnown = {
                        viewModel.markAsKnown()
                        Toast.makeText(context, context.getString(R.string.detail_known_toast), Toast.LENGTH_SHORT).show()
                    },
                    onEntryClick = onEntryClick,
                    onTopicClick = onTopicClick,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private data class SenseGroup(
    val senseNumber: Int,
    val translations: List<Translation>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: EntryDetail,
    preferredLanguage: Language,
    speaker: TtsSpeaker,
    speechRate: Float,
    fontScale: Float,
    cardProgress: FlashcardEntity?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onQueueForPractice: () -> Unit,
    onMarkKnown: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val senseGroups = remember(detail, preferredLanguage) {
        val allTranslations = detail.translations.values.flatten()
        val byPosition = allTranslations.groupBy { it.position }.entries.sortedBy { it.key }
        byPosition.mapIndexed { idx, (_, list) ->
            val ordered = list.sortedWith(
                compareBy<Translation> { if (it.language == preferredLanguage) 0 else 1 }
                    .thenBy { it.language.ordinal },
            )
            SenseGroup(senseNumber = idx + 1, translations = ordered)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Word Header Card
        item(key = "hero_header") {
            AppCard(cornerRadius = 20.dp) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LanguageTag(detail.entry.language)
                        detail.entry.pos?.let { PosBadge(it.label) }
                        detail.entry.topic?.let { topic ->
                            Surface(
                                onClick = { onTopicClick(topic) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            ) {
                                Text(
                                    text = topic.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    val baseHeadline = MaterialTheme.typography.displaySmall
                    Text(
                        text = detail.entry.headword,
                        style = baseHeadline.copy(fontSize = baseHeadline.fontSize * fontScale),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (detail.entry.language == Language.TAJIK) {
                        val latin = TajikTransliterator.toLatin(detail.entry.headword)
                        if (latin.isNotEmpty()) {
                            Text(
                                text = "/$latin/",
                                style = MaterialTheme.typography.titleMedium,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (speaker.canSpeak(detail.entry.language)) {
                            CompactActionPill(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                label = stringResource(R.string.listen),
                                highlighted = true,
                                onClick = {
                                    speaker.speak(
                                        detail.entry.headword,
                                        detail.entry.language,
                                        speechRate,
                                        "entry_${detail.entry.id}",
                                    )
                                },
                            )
                        }
                        CompactActionPill(
                            icon = Icons.Filled.ContentCopy,
                            label = stringResource(R.string.copy),
                            onClick = onCopy,
                        )
                        CompactActionPill(
                            icon = Icons.Filled.Share,
                            label = stringResource(R.string.share),
                            onClick = onShare,
                        )
                    }
                }
            }
        }

        // Other meanings / parts of speech (Homographs, e.g. соҳибӣ n. vs adj.)
        if (detail.homographs.isNotEmpty()) {
            item(key = "homographs_header") {
                SectionHeader(title = stringResource(R.string.detail_other_pos))
            }
            item(key = "homographs_card") {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    detail.homographs.forEachIndexed { idx, h ->
                        HomographRow(item = h, onClick = { onEntryClick(h.id) })
                        if (idx < detail.homographs.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        // Sense-grouped Translations
        if (senseGroups.isNotEmpty()) {
            item(key = "senses_card") {
                AppCard {
                    senseGroups.forEachIndexed { groupIdx, group ->
                        Column {
                            if (senseGroups.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${group.senseNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.detail_sense_n, group.senseNumber),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            group.translations.forEachIndexed { idx, translation ->
                                TranslationSenseRow(
                                    translation = translation,
                                    canSpeak = speaker.canSpeak(translation.language),
                                    onSpeak = {
                                        speaker.speak(
                                            translation.headword,
                                            translation.language,
                                            speechRate,
                                            "tr_${translation.entryId}",
                                        )
                                    },
                                    onClick = { onEntryClick(translation.entryId) },
                                )
                                if (idx < group.translations.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }

                        if (groupIdx < senseGroups.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        // Examples
        if (detail.examples.isNotEmpty()) {
            item(key = "examples_header") {
                SectionHeader(title = stringResource(R.string.examples))
            }
            items(detail.examples) { ex ->
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = ex.tg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val exLatin = TajikTransliterator.toLatin(ex.tg)
                                if (exLatin.isNotEmpty()) {
                                    Text(
                                        text = "/$exLatin/",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (speaker.canSpeak(Language.TAJIK)) {
                                IconButton(
                                    onClick = { speaker.speak(ex.tg, Language.TAJIK, speechRate, "ex_tg") },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = stringResource(R.string.listen),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        ex.ru?.let { ruText ->
                            ExampleTranslationLine(
                                language = Language.RUSSIAN,
                                text = ruText,
                                canSpeak = speaker.canSpeak(Language.RUSSIAN),
                                onSpeak = { speaker.speak(ruText, Language.RUSSIAN, speechRate, "ex_ru") },
                            )
                        }
                        ex.en?.let { enText ->
                            ExampleTranslationLine(
                                language = Language.ENGLISH,
                                text = enText,
                                canSpeak = speaker.canSpeak(Language.ENGLISH),
                                onSpeak = { speaker.speak(enText, Language.ENGLISH, speechRate, "ex_en") },
                            )
                        }
                    }
                }
            }
        }

        // Synonyms
        if (detail.synonyms.isNotEmpty()) {
            item(key = "synonyms_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.detail_synonyms))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.synonyms.forEach { item ->
                            RelatedChip(item = item, onClick = { onEntryClick(item.id) })
                        }
                    }
                }
            }
        }

        // Related Words
        if (detail.relatedWords.isNotEmpty()) {
            item(key = "related_section") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.detail_related_words))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.relatedWords.forEach { item ->
                            RelatedChip(item = item, onClick = { onEntryClick(item.id) })
                        }
                    }
                }
            }
        }

        // Integrated Learning Card (Word Trainer)
        if (detail.entry.language == Language.TAJIK) {
            item(key = "learning_progress") {
                val isMastered = cardProgress != null && (cardProgress.isKnownSkipped || cardProgress.masteryStage >= 4)
                val statusLabel = when {
                    cardProgress == null -> stringResource(R.string.detail_learning_unstudied)
                    isMastered -> stringResource(R.string.detail_learning_mastered)
                    else -> stringResource(R.string.detail_learning_stage, cardProgress.masteryStage)
                }
                val stage = when {
                    cardProgress == null -> 0
                    isMastered -> 4
                    else -> cardProgress.masteryStage.coerceIn(0, 4)
                }

                AppCard(
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ) {
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
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    stringResource(R.string.detail_learning_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isMastered) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMastered) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // 4-step mastery indicator
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 1..4) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (i <= stage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onQueueForPractice,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (cardProgress == null) {
                                        stringResource(R.string.detail_add_to_practice)
                                    } else {
                                        stringResource(R.string.detail_learning_practice_again)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!isMastered) {
                                OutlinedButton(
                                    onClick = onMarkKnown,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.detail_mark_known),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CompactActionPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HomographRow(
    item: SearchResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.pos?.let { PosBadge(it.label) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.headword,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.preview.isNotEmpty()) {
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslationSenseRow(
    translation: Translation,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageTag(translation.language)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = translation.headword,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                translation.pos?.let { PosBadge(it.label) }
            }
            if (translation.language == Language.TAJIK) {
                val latin = TajikTransliterator.toLatin(translation.headword)
                if (latin.isNotEmpty()) {
                    Text(
                        text = "/$latin/",
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            translation.note?.let { note ->
                Text(
                    text = "($note)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canSpeak) {
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.listen),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExampleTranslationLine(
    language: Language,
    text: String,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageTag(language)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canSpeak) {
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.listen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RelatedChip(
    item: SearchResult,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.headword,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.pos?.let {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatForShare(detail: EntryDetail): String = buildString {
    append("${detail.entry.headword} [${detail.entry.language.tag}]")
    detail.entry.pos?.let { append(" (${it.label})") }
    detail.translations.forEach { (lang, list) ->
        append("\n${lang.tag}: ${list.joinToString(", ") { it.headword }}")
    }
}

private fun copyEntry(context: Context, detail: EntryDetail) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(detail.entry.headword, formatForShare(detail)))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

private fun shareEntry(context: Context, detail: EntryDetail) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, formatForShare(detail))
    }
    context.startActivity(Intent.createChooser(intent, detail.entry.headword))
}

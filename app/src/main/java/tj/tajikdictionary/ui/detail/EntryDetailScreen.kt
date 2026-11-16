package tj.tajikdictionary.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.data.user.FlashcardEntity
import tj.tajikdictionary.domain.model.EntryDetail
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.Translation
import tj.tajikdictionary.ui.components.LanguageTag
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    viewModel: EntryDetailViewModel,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        instance
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.entry?.headword.orEmpty()) },
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
                    CircularProgressIndicator()
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
                    canSpeak = ttsReady && detail.entry.language != Language.TAJIK,
                    onSpeak = {
                        val locale = when (detail.entry.language) {
                            Language.RUSSIAN -> Locale.forLanguageTag("ru")
                            Language.ENGLISH -> Locale.US
                            Language.TAJIK -> null
                        }
                        if (locale != null) {
                            tts.language = locale
                            tts.setSpeechRate(state.speechRate)
                            tts.speak(detail.entry.headword, TextToSpeech.QUEUE_FLUSH, null, "entry_${detail.entry.id}")
                        }
                    },
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
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: EntryDetail,
    preferredLanguage: Language,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    fontScale: Float,
    cardProgress: FlashcardEntity?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onQueueForPractice: () -> Unit,
    onMarkKnown: () -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedGroups: List<Pair<Language, List<Translation>>> = remember(detail, preferredLanguage) {
        detail.translations.entries
            .sortedBy { (lang, _) -> if (lang == preferredLanguage) 0 else 1 }
            .map { it.key to it.value }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageTag(detail.entry.language)
                    detail.entry.pos?.let {
                        Text(
                            it.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    detail.entry.topic?.let { topic ->
                        AssistChip(onClick = {}, label = { Text(topic) })
                    }
                }
                val baseHeadline = MaterialTheme.typography.headlineLarge
                Text(
                    detail.entry.headword,
                    style = baseHeadline.copy(fontSize = baseHeadline.fontSize * fontScale),
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canSpeak) {
                        FilledTonalButton(onClick = onSpeak) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(stringResource(R.string.listen))
                        }
                    }
                    FilledTonalButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(stringResource(R.string.copy))
                    }
                    FilledTonalButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(stringResource(R.string.share))
                    }
                }
            }
        }

        orderedGroups.forEach { (lang, items) ->
            item(key = "section_${lang.code}") {
                Text(
                    text = languageDisplayName(lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item(key = "card_${lang.code}") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        items.forEachIndexed { idx, translation ->
                            ListItem(
                                modifier = Modifier.clickable { onEntryClick(translation.entryId) },
                                leadingContent = { LanguageTag(translation.language) },
                                headlineContent = {
                                    Text(translation.headword, fontWeight = FontWeight.Medium)
                                },
                                supportingContent = translation.note?.let { note ->
                                    { Text("($note)") }
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                            )
                            if (idx < items.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        if (detail.examples.isNotEmpty()) {
            item(key = "examples_header") {
                Text(
                    stringResource(R.string.examples),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(detail.examples) { ex ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(ex.tg, fontWeight = FontWeight.SemiBold)
                        ex.ru?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        ex.en?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        if (detail.entry.language == Language.TAJIK) {
            item(key = "learning_progress") {
                val statusLabel = when {
                    cardProgress == null -> stringResource(R.string.detail_learning_unstudied)
                    cardProgress.isKnownSkipped || cardProgress.masteryStage >= 4 -> stringResource(R.string.detail_learning_mastered)
                    else -> stringResource(R.string.detail_learning_stage, cardProgress.masteryStage)
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.detail_learning_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onQueueForPractice) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Text(stringResource(R.string.detail_add_to_practice))
                            }
                            if (cardProgress == null || (!cardProgress.isKnownSkipped && cardProgress.masteryStage < 4)) {
                                OutlinedButton(onClick = onMarkKnown) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 3.dp))
                                    Text(stringResource(R.string.detail_mark_known))
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
private fun languageDisplayName(language: Language): String = when (language) {
    Language.TAJIK -> stringResource(R.string.lang_tajik)
    Language.RUSSIAN -> stringResource(R.string.lang_russian)
    Language.ENGLISH -> stringResource(R.string.lang_english)
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

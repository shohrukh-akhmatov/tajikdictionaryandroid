package tj.tajikdictionary.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.core.text.TajikTransliterator
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.SearchMatchType
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.EmptyState
import tj.tajikdictionary.ui.components.LanguageTag
import tj.tajikdictionary.ui.components.ModeSwitcher
import tj.tajikdictionary.ui.components.PosBadge
import tj.tajikdictionary.ui.components.SectionHeader
import tj.tajikdictionary.ui.components.TajikLettersRow
import tj.tajikdictionary.ui.components.WordRow
import tj.tajikdictionary.ui.components.rememberTtsSpeaker

private enum class SearchViewState {
    HOME,
    SUGGEST_MODE,
    EMPTY,
    RESULTS,
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onEntryClick: (Long) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val wordOfTheDay by viewModel.wordOfTheDay.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val speaker = rememberTtsSpeaker()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Integrated search & mode header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 0.5.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeSwitcher(mode = mode, onModeChange = viewModel::onModeChange)

                val placeholder = when (mode) {
                    DictionaryMode.TJ_RU -> stringResource(R.string.search_hint_tj_ru)
                    DictionaryMode.TJ_EN -> stringResource(R.string.search_hint_tj_en)
                }
                CompactSearchBar(
                    query = query,
                    placeholder = placeholder,
                    onQueryChange = viewModel::onQueryChange,
                    onSearchAction = { keyboard?.hide() },
                )

                TajikLettersRow(onLetter = viewModel::appendLetter)
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )

        val viewState = when {
            query.isBlank() -> SearchViewState.HOME
            outcome.suggestedMode != null -> SearchViewState.SUGGEST_MODE
            outcome.results.isEmpty() -> SearchViewState.EMPTY
            else -> SearchViewState.RESULTS
        }

        AnimatedContent(
            targetState = viewState,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "searchContent",
            modifier = Modifier.weight(1f),
        ) { state ->
            when (state) {
                SearchViewState.HOME -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    ) {
                        wordOfTheDay?.let { wotd ->
                            item(key = "wotd") {
                                val canSpeakWotd = speaker.canSpeak(wotd.entry.language) ||
                                    (wotd.preview.isNotEmpty() && speaker.canSpeak(mode.partner))
                                WordOfTheDayCard(
                                    item = wotd,
                                    canSpeak = canSpeakWotd,
                                    onSpeak = {
                                        if (speaker.canSpeak(wotd.entry.language)) {
                                            speaker.speak(wotd.entry.headword, wotd.entry.language, speechRate, "wotd")
                                        } else if (wotd.preview.isNotEmpty()) {
                                            val firstMeaning = wotd.preview.substringBefore(',').trim()
                                            speaker.speak(firstMeaning, mode.partner, speechRate, "wotd_tr")
                                        }
                                    },
                                    onClick = { onEntryClick(wotd.entry.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                Spacer(Modifier.height(20.dp))
                            }
                        }

                        if (topics.isNotEmpty()) {
                            item(key = "topics_header") {
                                SectionHeader(
                                    title = stringResource(R.string.topics_title),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            item(key = "topics_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(topics, key = { it.topic }) { item ->
                                        TopicPill(
                                            topic = item.topic,
                                            count = item.count,
                                            onClick = { onTopicClick(item.topic) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }

                        if (recent.isNotEmpty()) {
                            val recentItems = recent.take(15)
                            item(key = "recent_header") {
                                SectionHeader(
                                    title = stringResource(R.string.recent_searches),
                                    actionLabel = stringResource(R.string.clear),
                                    onAction = viewModel::clearHistory,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            item(key = "recent_card") {
                                AppCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    recentItems.forEachIndexed { idx, item ->
                                        WordRow(
                                            language = Language.fromCode(item.lang),
                                            headword = item.headword,
                                            preview = item.preview,
                                            onClick = { onEntryClick(item.entryId) },
                                        )
                                        if (idx < recentItems.lastIndex) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SearchViewState.SUGGEST_MODE -> {
                    val suggested = outcome.suggestedMode
                    if (suggested != null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.wrong_mode_hint, suggested.title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.onModeChange(suggested) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.switch_to_mode, suggested.title))
                            }
                        }
                    }
                }

                SearchViewState.EMPTY -> {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.no_results_title),
                        message = stringResource(R.string.no_results_message, query.trim()),
                    )
                }

                SearchViewState.RESULTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (outcome.matchType != SearchMatchType.DIRECT) {
                            item(key = "fallback_banner") {
                                val bannerText = when (outcome.matchType) {
                                    SearchMatchType.STEM -> stringResource(R.string.match_stem_hint, query.trim())
                                    SearchMatchType.FUZZY -> stringResource(R.string.match_fuzzy_hint)
                                    SearchMatchType.DIRECT -> ""
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = bannerText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        item(key = "results_card") {
                            AppCard {
                                outcome.results.forEachIndexed { index, row ->
                                    WordRow(
                                        language = row.language,
                                        headword = row.headword,
                                        preview = row.preview,
                                        pos = row.pos,
                                        onClick = { onEntryClick(row.id) },
                                    )
                                    if (index < outcome.results.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
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
private fun CompactSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSearchAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (query.isNotEmpty()) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicPill(
    topic: String,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = topic.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun WordOfTheDayCard(
    item: WordOfTheDayUi,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        onClick = onClick,
    ) {
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.word_of_the_day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LanguageTag(item.entry.language)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.entry.headword,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        item.entry.pos?.let { PosBadge(it.label) }
                    }
                    if (item.entry.language == Language.TAJIK) {
                        val latin = TajikTransliterator.toLatin(item.entry.headword)
                        if (latin.isNotEmpty()) {
                            Text(
                                text = "/$latin/",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (canSpeak) {
                    Surface(
                        onClick = onSpeak,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.listen),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (item.preview.isNotEmpty()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

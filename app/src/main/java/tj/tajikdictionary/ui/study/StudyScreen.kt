package tj.tajikdictionary.ui.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import tj.tajikdictionary.core.text.TajikTransliterator
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.localizedTopicName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.max
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.ReviewRating
import tj.tajikdictionary.domain.model.TopicCount
import tj.tajikdictionary.ui.components.EmptyState
import tj.tajikdictionary.ui.components.LanguageTag

data class StudyCard(
    val entryId: Long,
    val language: Language,
    val headword: String,
    val preview: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    initialTopic: String? = null,
    onBack: () -> Unit,
) {
    val mode by settings.studyMode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_EN)
    val favorites by userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val topics by produceState<List<TopicCount>>(initialValue = emptyList(), dictionary) {
        value = dictionary.topics()
    }

    var selectedTopic by remember(initialTopic) { mutableStateOf(initialTopic) }
    var cards by remember { mutableStateOf<List<StudyCard>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var reviewedCount by remember { mutableIntStateOf(0) }
    var cardPresentedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTopic, favorites, mode) {
        val now = System.currentTimeMillis()
        val dueMap = progress.associateBy { it.entryId }
        val raw = if (selectedTopic == null) {
            val resolvedById = dictionary.learningCandidatesForIds(favorites.map { it.entryId }, mode)
                .associateBy { it.entryId }
            favorites.mapNotNull { fav ->
                val cand = resolvedById[fav.entryId]
                if (cand != null && cand.preview.isNotBlank()) {
                    StudyCard(cand.entryId, cand.language, cand.headword, cand.preview)
                } else {
                    val freshPreview = dictionary.preview(fav.entryId, mode).ifBlank { fav.preview }
                    if (freshPreview.isNotBlank()) {
                        StudyCard(fav.entryId, Language.fromCode(fav.lang), fav.headword, freshPreview)
                    } else {
                        null
                    }
                }
            }
        } else {
            dictionary.entriesByTopic(selectedTopic!!, mode)
                .filter { it.preview.isNotBlank() }
                .map { StudyCard(it.id, it.language, it.headword, it.preview) }
        }
        // Prioritize cards that are due or never studied yet.
        cards = raw.sortedBy { card -> dueMap[card.entryId]?.nextReviewAt ?: 0L }
            .let { list ->
                val due = list.filter { (dueMap[it.entryId]?.nextReviewAt ?: 0L) <= now }
                if (due.isNotEmpty()) due else list
            }
        currentIndex = 0
        flipped = false
        reviewedCount = 0
        cardPresentedAtMs = System.currentTimeMillis()
    }

    val deckTitle = selectedTopic?.let { stringResource(R.string.study_topic_title, localizedTopicName(it)) }
        ?: stringResource(R.string.study_flashcards)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    StudyLanguageTitle(deckTitle, settings)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "deck_saved") {
                    AssistChip(
                        onClick = { selectedTopic = null },
                        label = {
                            Text(
                                if (selectedTopic == null) "✓ ${stringResource(R.string.tab_saved)}"
                                else stringResource(R.string.tab_saved),
                            )
                        },
                    )
                }
                items(topics, key = { it.topic }) { topic ->
                    val name = localizedTopicName(topic.topic)
                    AssistChip(
                        onClick = { selectedTopic = topic.topic },
                        label = {
                            Text(if (selectedTopic == topic.topic) "✓ $name" else name)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                cards.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.School,
                        title = stringResource(R.string.study_empty_title),
                        message = stringResource(R.string.study_empty_message),
                    )
                }

                currentIndex >= cards.size -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.study_done_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.study_done_count, reviewedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                currentIndex = 0
                                reviewedCount = 0
                                flipped = false
                            },
                        ) { Text(stringResource(R.string.study_restart)) }
                    }
                }

                else -> {
                    val card = cards[currentIndex]
                    LinearProgressIndicator(
                        progress = { (currentIndex.toFloat() / cards.size.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${currentIndex + 1} / ${cards.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    // Same path as the buttons: progress, XP and the daily log all update.
                    // Works on every card, including the last one (it then shows "Session complete").
                    val grade: (ReviewRating) -> Unit = { rating ->
                        val elapsedMs = max(500L, System.currentTimeMillis() - cardPresentedAtMs)
                        scope.launch {
                            userData.reviewFlashcard(
                                card.entryId,
                                card.language,
                                card.headword,
                                card.preview,
                                rating,
                                elapsedMs,
                            )
                        }
                        reviewedCount += 1
                        currentIndex += 1
                        flipped = false
                        cardPresentedAtMs = System.currentTimeMillis()
                    }

                    val density = LocalDensity.current
                    val thresholdPx = with(density) { SwipeThreshold.toPx() }
                    val flingPx = with(density) { SwipeFlingVelocity.toPx() }
                    // Keyed by entryId so the next card always starts centred.
                    val dragOffset = remember(card.entryId) { Animatable(0f) }
                    var flyingOut by remember(card.entryId) { mutableStateOf(false) }
                    val swipe: (Boolean) -> Unit = { right ->
                        if (!flyingOut) {
                            flyingOut = true
                            scope.launch {
                                val target = with(density) { 700.dp.toPx() }
                                dragOffset.animateTo(
                                    if (right) target else -target,
                                    tween(durationMillis = 200, easing = FastOutLinearInEasing),
                                )
                                grade(if (right) ReviewRating.GOOD else ReviewRating.AGAIN)
                            }
                        }
                    }
                    val goodLabel = stringResource(R.string.study_good)
                    val againLabel = stringResource(R.string.study_again)

                    AppCard(
                        cornerRadius = 20.dp,
                        onClick = { if (!flyingOut) flipped = !flipped },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer {
                                translationX = dragOffset.value
                                rotationZ = dragOffset.value / density.density / 25f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .pointerInput(card.entryId) {
                                val tracker = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = { tracker.resetTracking() },
                                    onDragCancel = {
                                        if (!flyingOut) scope.launch { dragOffset.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                    },
                                    onDragEnd = {
                                        if (!flyingOut) {
                                            val x = dragOffset.value
                                            val vx = tracker.calculateVelocity().x
                                            when {
                                                abs(x) > thresholdPx -> swipe(x > 0)
                                                abs(vx) > flingPx && sign(vx) == sign(x) -> swipe(vx > 0)
                                                else -> scope.launch {
                                                    dragOffset.animateTo(0f, spring(dampingRatio = 0.7f))
                                                }
                                            }
                                        }
                                    },
                                ) { change, dx ->
                                    if (!flyingOut) {
                                        change.consume()
                                        val next = dragOffset.value + dx
                                        // Track our own accumulated offset: the layer moves under the finger.
                                        tracker.addPosition(change.uptimeMillis, Offset(next, 0f))
                                        scope.launch { dragOffset.snapTo(next) }
                                    }
                                }
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(goodLabel) { grade(ReviewRating.GOOD); true },
                                    CustomAccessibilityAction(againLabel) { grade(ReviewRating.AGAIN); true },
                                )
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SwipeStamp(offsetPx = dragOffset.value, thresholdPx = thresholdPx)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LanguageTag(card.language)
                                Text(
                                    text = card.headword,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                if (card.language == Language.TAJIK) {
                                    val latin = TajikTransliterator.toLatin(card.headword)
                                    if (latin.isNotEmpty()) {
                                        Text(
                                            text = "/$latin/",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                if (flipped) {
                                    Text(
                                        text = card.preview.ifEmpty { "—" },
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.study_tap_to_reveal),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.study_swipe_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    if (!flipped) {
                        Button(
                            onClick = { if (!flyingOut) flipped = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.study_reveal)) }
                    } else {
                        val gradeIfIdle: (ReviewRating) -> Unit = { if (!flyingOut) grade(it) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { gradeIfIdle(ReviewRating.AGAIN) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text(stringResource(R.string.study_again)) }
                            FilledTonalButton(
                                onClick = { gradeIfIdle(ReviewRating.GOOD) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.study_good)) }
                            Button(
                                onClick = { gradeIfIdle(ReviewRating.EASY) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.study_easy)) }
                        }
                    }
                }
            }
        }
    }
}

/** Horizontal distance after which releasing the card counts as a swipe (iOS: 110 pt). */
private val SwipeThreshold = 110.dp

/** A fast fling in the drag's direction also counts as a swipe. */
private val SwipeFlingVelocity = 800.dp

/** "Good" / "Again" stamp that fades in while the card is dragged (iOS `swipeHint`). */
@Composable
private fun BoxScope.SwipeStamp(offsetPx: Float, thresholdPx: Float) {
    if (offsetPx == 0f) return
    val right = offsetPx > 0
    val color = if (right) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val label = stringResource(if (right) R.string.study_good else R.string.study_again)
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = color,
        modifier = Modifier
            .align(if (right) Alignment.TopStart else Alignment.TopEnd)
            .graphicsLayer {
                alpha = min(1f, abs(offsetPx) / thresholdPx)
                rotationZ = if (right) -12f else 12f
            }
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clearAndSetSemantics { },
    )
}

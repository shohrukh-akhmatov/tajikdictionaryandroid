package tj.tajikdictionary.ui.study

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.user.FlashcardEntity
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.ui.components.EmptyState
import tj.tajikdictionary.ui.components.keyboardDismissExempt
import kotlin.math.roundToInt

enum class VocabFilter {
    ALL,
    DUE,
    WEAK,
    LEARNING,
    MASTERED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyListScreen(
    userData: UserDataRepository,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onDrillWeak: () -> Unit,
) {
    val flashcards by userData.flashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(VocabFilter.ALL) }
    var search by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    fun isDue(c: FlashcardEntity): Boolean =
        !c.isKnownSkipped && c.lastReviewedAt != null && c.nextReviewAt <= now

    fun isWeak(c: FlashcardEntity): Boolean =
        !c.isKnownSkipped && (c.lapses > 0 || (c.lastReviewedAt != null && c.masteryStage <= 1) || c.stabilityDays < 2.0)

    fun isMastered(c: FlashcardEntity): Boolean =
        c.isKnownSkipped || c.masteryStage >= 3

    fun isLearning(c: FlashcardEntity): Boolean =
        !isMastered(c) && (c.lastReviewedAt != null || c.stabilityDays > 0.0)

    val counts = remember(flashcards, now) {
        mapOf(
            VocabFilter.ALL to flashcards.size,
            VocabFilter.DUE to flashcards.count(::isDue),
            VocabFilter.WEAK to flashcards.count(::isWeak),
            VocabFilter.LEARNING to flashcards.count(::isLearning),
            VocabFilter.MASTERED to flashcards.count(::isMastered),
        )
    }

    val filtered = remember(flashcards, filter, search, now) {
        val q = search.trim()
        flashcards.filter { card ->
            val matchesFilter = when (filter) {
                VocabFilter.ALL -> true
                VocabFilter.DUE -> isDue(card)
                VocabFilter.WEAK -> isWeak(card)
                VocabFilter.LEARNING -> isLearning(card)
                VocabFilter.MASTERED -> isMastered(card)
            }
            val matchesSearch = q.isEmpty() ||
                card.headword.contains(q, ignoreCase = true) ||
                card.preview.contains(q, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedWith(
            compareByDescending<FlashcardEntity> { isWeak(it) }
                .thenBy { it.nextReviewAt }
                .thenBy { it.headword.lowercase() },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.vocab_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if ((counts[VocabFilter.WEAK] ?: 0) > 0) {
                        TextButton(onClick = onDrillWeak) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.vocab_drill_weak))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .keyboardDismissExempt(),
                placeholder = { Text(stringResource(R.string.vocab_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(VocabFilter.entries) { f ->
                    val label = when (f) {
                        VocabFilter.ALL -> stringResource(R.string.vocab_filter_all)
                        VocabFilter.DUE -> stringResource(R.string.vocab_filter_due)
                        VocabFilter.WEAK -> stringResource(R.string.vocab_filter_weak)
                        VocabFilter.LEARNING -> stringResource(R.string.game_learning)
                        VocabFilter.MASTERED -> stringResource(R.string.game_mastered)
                    }
                    val count = counts[f] ?: 0
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("$label ($count)") },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.vocab_empty_title),
                    message = stringResource(R.string.vocab_empty_desc),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.entryId }) { card ->
                        val (badgeText, badgeColor) = when {
                            isMastered(card) -> stringResource(R.string.game_mastered) to Color(0xFF2E7D32)
                            isWeak(card) -> stringResource(R.string.vocab_filter_weak) to Color(0xFFD84315)
                            isDue(card) -> stringResource(R.string.vocab_due_now) to Color(0xFFEF6C00)
                            else -> stringResource(R.string.vocab_stage, card.masteryStage) to MaterialTheme.colorScheme.primary
                        }
                        ListItem(
                            modifier = Modifier.clickable { onEntryClick(card.entryId) },
                            headlineContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        card.headword,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = badgeColor.copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            badgeText,
                                            color = badgeColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (card.preview.isNotBlank()) {
                                        Text(
                                            card.preview,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val stability = card.stabilityDays.roundToInt()
                                    val meta = if (card.lapses > 0) {
                                        stringResource(R.string.vocab_meta_lapses, maxOf(1, stability), card.lapses)
                                    } else {
                                        stringResource(R.string.vocab_meta_interval, maxOf(1, stability))
                                    }
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            userData.queueWordForPractice(
                                                entryId = card.entryId,
                                                language = Language.fromCode(card.lang),
                                                headword = card.headword,
                                                preview = card.preview,
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.detail_queued_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                ) {
                                    Icon(
                                        Icons.Filled.AddCircleOutline,
                                        contentDescription = stringResource(R.string.detail_add_to_practice),
                                    )
                                }
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

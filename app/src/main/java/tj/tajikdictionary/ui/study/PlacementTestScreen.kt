package tj.tajikdictionary.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.CefrLevel
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.PlacementAnswer
import tj.tajikdictionary.domain.model.PlacementQuestion
import tj.tajikdictionary.domain.model.PlacementResult
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.ui.components.LanguageTag
import java.util.Locale

@Composable
internal fun cefrTitle(level: CefrLevel): String = when (level) {
    CefrLevel.A0 -> stringResource(R.string.cefr_a0)
    CefrLevel.A1 -> stringResource(R.string.cefr_a1)
    CefrLevel.A2 -> stringResource(R.string.cefr_a2)
    CefrLevel.B1 -> stringResource(R.string.cefr_b1)
    CefrLevel.B2 -> stringResource(R.string.cefr_b2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementTestScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onBack: () -> Unit,
) {
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PlacementQuestion>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf<List<PlacementAnswer>>(emptyList()) }
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PlacementResult?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(mode, reloadTrigger) {
        isLoading = true
        currentIndex = 0
        answers = emptyList()
        selectedChoice = null
        result = null
        val pool = dictionary.placementPool(mode)
        questions = WordGameEngine.buildPlacementQuestions(pool)
        isLoading = false
    }

    fun recordAndAdvance(question: PlacementQuestion, isCorrect: Boolean) {
        val nextAnswers = answers + PlacementAnswer(question, isCorrect)
        answers = nextAnswers
        selectedChoice = null
        if (currentIndex + 1 < questions.size) {
            currentIndex += 1
        } else {
            val evaluated = WordGameEngine.evaluatePlacement(nextAnswers)
            result = evaluated
            scope.launch {
                settings.applyPlacement(evaluated)
                val byId = questions.associate { it.candidate.entryId to it.candidate }
                userData.applyPlacementAnswers(
                    knownCards = evaluated.knownEntryIds.mapNotNull { byId[it] },
                    focusCards = evaluated.focusEntryIds.mapNotNull { byId[it] },
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.placement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            result != null -> {
                val res = result!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${res.cefrLevel.code} · ${cefrTitle(res.cefrLevel)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.placement_score, res.correctCount, res.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryStatTile(
                            value = String.format(Locale.US, "%.1f%%", res.textRecognitionPercent),
                            label = stringResource(R.string.placement_text_recognized),
                            modifier = Modifier.weight(1f),
                        )
                        SummaryStatTile(
                            value = "~${res.estimatedKnownWords}",
                            label = stringResource(R.string.placement_known_words),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.placement_calibrated_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.placement_start_at_level))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { reloadTrigger += 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.placement_retake))
                    }
                }
            }

            currentIndex < questions.size -> {
                val q = questions[currentIndex]
                val tierLabel = when (q.tier) {
                    1 -> stringResource(R.string.placement_tier_core)
                    2 -> stringResource(R.string.placement_tier_everyday)
                    3 -> stringResource(R.string.placement_tier_intermediate)
                    else -> stringResource(R.string.placement_tier_advanced)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { currentIndex.toFloat() / maxOf(1, questions.size).toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${currentIndex + 1} / ${questions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                tierLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LanguageTag(q.candidate.language)
                                q.candidate.pos?.let { pos ->
                                    Text(
                                        pos.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                q.candidate.headword,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.placement_choose_meaning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        q.choices.forEach { choice ->
                            val containerColor = when {
                                selectedChoice == null -> MaterialTheme.colorScheme.surfaceVariant
                                choice == q.candidate.preview -> Color(0xFF2E7D32).copy(alpha = 0.2f)
                                choice == selectedChoice -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                            Surface(
                                color = containerColor,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Button(
                                    onClick = {
                                        if (selectedChoice == null) {
                                            selectedChoice = choice
                                            val isCorrect = choice == q.candidate.preview
                                            scope.launch {
                                                delay(250)
                                                recordAndAdvance(q, isCorrect)
                                            }
                                        }
                                    },
                                    enabled = selectedChoice == null,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        choice,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { if (selectedChoice == null) recordAndAdvance(q, isCorrect = false) },
                        enabled = selectedChoice == null,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(R.string.game_dont_know),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

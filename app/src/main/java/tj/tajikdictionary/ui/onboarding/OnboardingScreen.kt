package tj.tajikdictionary.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.domain.model.DictionaryMode

private const val STEP_COUNT = 4

/**
 * First-launch flow: welcome → language → daily goal → level (iOS `OnboardingView`). Every step can be skipped
 * and everything chosen here can be changed later in Settings or on the Learn screen.
 *
 * [onFinish] is called once at the end; `startPlacement` is true when the user chose to check their level now.
 */
@Composable
fun OnboardingScreen(
    settings: SettingsRepository,
    onFinish: (startPlacement: Boolean) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = null)
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 0)

    val next = { step = (step + 1).coerceAtMost(STEP_COUNT - 1) }

    BackHandler(enabled = step > 0) { step -= 1 }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    IconButton(onClick = { step -= 1 }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step > 0) {
                    TextButton(onClick = { onFinish(false) }) {
                        Text(stringResource(R.string.onboarding_skip), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
                label = "onboardingStep",
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        0 -> WelcomeStep(onStart = next)
                        1 -> StepBody(
                            title = stringResource(R.string.onboarding_language_title),
                            subtitle = stringResource(R.string.onboarding_language_sub),
                        ) {
                            val tajik = stringResource(R.string.lang_tajik)
                            listOf(
                                Triple(DictionaryMode.TJ_RU, "Русский", stringResource(R.string.lang_russian)),
                                Triple(DictionaryMode.TJ_EN, "English", stringResource(R.string.lang_english)),
                            ).forEach { (option, native, partner) ->
                                ChoiceCard(
                                    icon = Icons.Filled.Language,
                                    title = native,
                                    subtitle = "$tajik ⇄ $partner",
                                    selected = mode == option,
                                ) {
                                    scope.launch {
                                        settings.setMode(option)
                                        settings.setStudyMode(option)
                                    }
                                    next()
                                }
                            }
                        }
                        2 -> StepBody(
                            title = stringResource(R.string.onboarding_goal_title),
                            subtitle = stringResource(R.string.onboarding_goal_sub),
                        ) {
                            listOf(
                                5 to R.string.onboarding_goal_casual,
                                10 to R.string.onboarding_goal_regular,
                                15 to R.string.onboarding_goal_serious,
                                20 to R.string.onboarding_goal_intense,
                            ).forEach { (goal, label) ->
                                ChoiceCard(
                                    icon = Icons.Filled.TrackChanges,
                                    title = stringResource(label),
                                    subtitle = stringResource(R.string.onboarding_goal_words_a_day, goal),
                                    selected = dailyGoal == goal,
                                ) {
                                    scope.launch { settings.setDailyGoal(goal) }
                                    next()
                                }
                            }
                        }
                        else -> StepBody(
                            title = stringResource(R.string.onboarding_level_title),
                            subtitle = stringResource(R.string.onboarding_level_sub),
                        ) {
                            ChoiceCard(
                                icon = Icons.Filled.Tune,
                                title = stringResource(R.string.onboarding_level_check),
                                subtitle = stringResource(R.string.onboarding_level_check_sub),
                                selected = false,
                            ) { onFinish(true) }
                            ChoiceCard(
                                icon = Icons.Filled.Eco,
                                title = stringResource(R.string.onboarding_level_beginner),
                                subtitle = stringResource(R.string.onboarding_level_beginner_sub),
                                selected = false,
                            ) { onFinish(false) }
                        }
                    }
                }
            }

            PageDots(step = step, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun ColumnScope.WelcomeStep(onStart: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Луғати тоҷикӣ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FeatureRow(Icons.Filled.Search, stringResource(R.string.onboarding_feature_search))
            FeatureRow(Icons.Filled.SportsEsports, stringResource(R.string.onboarding_feature_game))
            FeatureRow(Icons.Filled.Star, stringResource(R.string.onboarding_feature_save))
        }
    }
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 0.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(stringResource(R.string.onboarding_get_started), style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.StepBody(title: String, subtitle: String, options: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.padding(top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = options)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageDots(step: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(STEP_COUNT) { index ->
            val width by animateDpAsState(if (index == step) 20.dp else 8.dp, label = "dot$index")
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (index == step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        }
    }
}

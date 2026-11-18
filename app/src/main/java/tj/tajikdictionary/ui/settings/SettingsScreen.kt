package tj.tajikdictionary.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.FontScaleSetting
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.data.settings.ThemeSetting
import tj.tajikdictionary.data.user.FavoritesBackup
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.DictionaryInfo
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.ModeSwitcher
import tj.tajikdictionary.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onOpenGrammarGuide: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenPlacement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
    val speechRate by settings.speechRate.collectAsStateWithLifecycle(initialValue = 1.0f)
    val fontScale by settings.fontScale.collectAsStateWithLifecycle(initialValue = FontScaleSetting.MEDIUM)
    val dailyGoal by settings.dailyGoal.collectAsStateWithLifecycle(initialValue = 10)
    val placementLevel by settings.placementLevel.collectAsStateWithLifecycle(initialValue = null)
    val hasCompletedPlacement by settings.hasCompletedPlacement.collectAsStateWithLifecycle(initialValue = false)
    val info by produceState<DictionaryInfo?>(initialValue = null, dictionary) {
        value = dictionary.info()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val favs = userData.favorites.first()
            val cards = userData.flashcards.first()
            val logs = userData.dailyStats.first()
            val placement = if (hasCompletedPlacement && placementLevel != null) {
                val lvl = placementLevel!!
                FavoritesBackup.PlacementBackup(
                    cefrLevel = lvl.code,
                    ceilingFreq = lvl.recommendedCeilingFrequency,
                    baselineWords = lvl.baselineKnownWords,
                )
            } else {
                null
            }
            val payload = FavoritesBackup.encodeFull(favs, cards, logs, placement)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            val msg = if (ok) {
                context.getString(R.string.backup_exported, favs.size + cards.size)
            } else {
                context.getString(R.string.backup_failed)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Empty file")
                    FavoritesBackup.decodeFull(json)
                }.mapCatching { decoded ->
                    val written = userData.importFullBackup(decoded)
                    decoded.placement?.let { p ->
                        settings.applyPlacementBackup(p.cefrLevel, p.ceilingFreq, p.baselineWords)
                    }
                    written
                }.getOrNull()
            }
            val msg = if (count != null) {
                context.getString(R.string.backup_imported, count)
            } else {
                context.getString(R.string.backup_failed)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    val transparentRow = ListItemDefaults.colors(containerColor = Color.Transparent)
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.Bold) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(title = stringResource(R.string.settings_default_mode))
            AppCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    ModeSwitcher(
                        mode = mode,
                        onModeChange = { newMode -> scope.launch { settings.setMode(newMode) } },
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.settings_appearance))
            AppCard {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val themes = ThemeSetting.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themes.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = item == theme,
                                onClick = { scope.launch { settings.setTheme(item) } },
                                shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                                colors = segmentedColors,
                            ) {
                                Text(
                                    when (item) {
                                        ThemeSetting.SYSTEM -> stringResource(R.string.theme_system)
                                        ThemeSetting.LIGHT -> stringResource(R.string.theme_light)
                                        ThemeSetting.DARK -> stringResource(R.string.theme_dark)
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.settings_font_size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val scales = FontScaleSetting.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        scales.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = item == fontScale,
                                onClick = { scope.launch { settings.setFontScale(item) } },
                                shape = SegmentedButtonDefaults.itemShape(index, scales.size),
                                colors = segmentedColors,
                            ) {
                                Text(
                                    when (item) {
                                        FontScaleSetting.SMALL -> stringResource(R.string.font_small)
                                        FontScaleSetting.MEDIUM -> stringResource(R.string.font_medium)
                                        FontScaleSetting.LARGE -> stringResource(R.string.font_large)
                                    },
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.settings_speech_speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val speeds = listOf(0.75f to "0.75×", 1.0f to "1.0×")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        speeds.forEachIndexed { index, (rate, label) ->
                            SegmentedButton(
                                selected = kotlin.math.abs(speechRate - rate) < 0.05f,
                                onClick = { scope.launch { settings.setSpeechRate(rate) } },
                                shape = SegmentedButtonDefaults.itemShape(index, speeds.size),
                                colors = segmentedColors,
                            ) { Text(label) }
                        }
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.settings_learning))
            AppCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_daily_goal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val goals = listOf(5, 10, 15, 20)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        goals.forEachIndexed { index, goal ->
                            SegmentedButton(
                                selected = goal == dailyGoal,
                                onClick = { scope.launch { settings.setDailyGoal(goal) } },
                                shape = SegmentedButtonDefaults.itemShape(index, goals.size),
                                colors = segmentedColors,
                            ) { Text("$goal") }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable(onClick = onOpenPlacement),
                    leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.placement_title)) },
                    supportingContent = {
                        Text(
                            if (hasCompletedPlacement && placementLevel != null) {
                                stringResource(R.string.placement_current_level, placementLevel!!.code)
                            } else {
                                stringResource(R.string.placement_settings_sub)
                            },
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable(onClick = onOpenStudy),
                    leadingContent = { Icon(Icons.Filled.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.study_flashcards)) },
                    supportingContent = { Text(stringResource(R.string.study_flashcards_sub)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable(onClick = onOpenGrammarGuide),
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.grammar_guide_title)) },
                    supportingContent = { Text(stringResource(R.string.grammar_guide_sub)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
            }

            SectionHeader(title = stringResource(R.string.settings_backup))
            AppCard {
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable { exportLauncher.launch("tajik-dictionary-favorites.json") },
                    leadingContent = { Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.backup_export_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_export_sub)) },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable {
                        importLauncher.launch(arrayOf("application/json", "text/*"))
                    },
                    leadingContent = { Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.backup_import_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_import_sub)) },
                )
            }

            SectionHeader(title = stringResource(R.string.settings_about))
            AppCard {
                ListItem(
                    colors = transparentRow,
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_app_subtitle)) },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    leadingContent = { Icon(Icons.Filled.Update, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text(stringResource(R.string.settings_data_version)) },
                    supportingContent = { Text(info?.dataVersion?.ifEmpty { "—" } ?: "…") },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text(stringResource(R.string.settings_stats)) },
                    supportingContent = { Text(info?.stats?.ifEmpty { "—" } ?: "…") },
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ListItem(
                    colors = transparentRow,
                    modifier = Modifier.clickable {
                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@tajikdictionary.tj"))
                            .putExtra(Intent.EXTRA_SUBJECT, "Tajik Dictionary feedback")
                        runCatching { context.startActivity(mail) }
                    },
                    leadingContent = { Icon(Icons.Filled.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.settings_report_mistake)) },
                    supportingContent = { Text(stringResource(R.string.settings_report_mistake_sub)) },
                )
            }
        }
    }
}

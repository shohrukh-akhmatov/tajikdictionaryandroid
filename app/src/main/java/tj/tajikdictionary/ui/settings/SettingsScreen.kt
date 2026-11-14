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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import tj.tajikdictionary.ui.components.ModeSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dictionary: DictionaryRepository,
    userData: UserDataRepository,
    settings: SettingsRepository,
    onOpenGrammarGuide: () -> Unit,
    onOpenStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeSetting.SYSTEM)
    val speechRate by settings.speechRate.collectAsStateWithLifecycle(initialValue = 1.0f)
    val fontScale by settings.fontScale.collectAsStateWithLifecycle(initialValue = FontScaleSetting.MEDIUM)
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
            val items = userData.favorites.first()
            val payload = FavoritesBackup.encode(items)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            val msg = if (ok) {
                context.getString(R.string.backup_exported, items.size)
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
                    FavoritesBackup.decode(json)
                }.map { decoded -> userData.importFavorites(decoded) }.getOrNull()
            }
            val msg = if (count != null) {
                context.getString(R.string.backup_imported, count)
            } else {
                context.getString(R.string.backup_failed)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
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
            Text(stringResource(R.string.settings_default_mode), style = MaterialTheme.typography.titleSmall)
            ModeSwitcher(
                mode = mode,
                onModeChange = { newMode -> scope.launch { settings.setMode(newMode) } },
            )

            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleSmall)
            val themes = ThemeSetting.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themes.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == theme,
                        onClick = { scope.launch { settings.setTheme(item) } },
                        shape = SegmentedButtonDefaults.itemShape(index, themes.size),
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

            Text(stringResource(R.string.settings_speech_speed), style = MaterialTheme.typography.titleSmall)
            val speeds = listOf(0.75f to "0.75×", 1.0f to "1.0×")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                speeds.forEachIndexed { index, (rate, label) ->
                    SegmentedButton(
                        selected = kotlin.math.abs(speechRate - rate) < 0.05f,
                        onClick = { scope.launch { settings.setSpeechRate(rate) } },
                        shape = SegmentedButtonDefaults.itemShape(index, speeds.size),
                    ) { Text(label) }
                }
            }

            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.titleSmall)
            val scales = FontScaleSetting.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                scales.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = item == fontScale,
                        onClick = { scope.launch { settings.setFontScale(item) } },
                        shape = SegmentedButtonDefaults.itemShape(index, scales.size),
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

            Text(stringResource(R.string.settings_learning), style = MaterialTheme.typography.titleSmall)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenStudy),
                        headlineContent = { Text(stringResource(R.string.study_flashcards)) },
                        supportingContent = { Text(stringResource(R.string.study_flashcards_sub)) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenGrammarGuide),
                        headlineContent = { Text(stringResource(R.string.grammar_guide_title)) },
                        supportingContent = { Text(stringResource(R.string.grammar_guide_sub)) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                }
            }

            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleSmall)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable { exportLauncher.launch("tajik-dictionary-favorites.json") },
                        headlineContent = { Text(stringResource(R.string.backup_export_title)) },
                        supportingContent = { Text(stringResource(R.string.backup_export_sub)) },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    ListItem(
                        modifier = Modifier.clickable {
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        headlineContent = { Text(stringResource(R.string.backup_import_title)) },
                        supportingContent = { Text(stringResource(R.string.backup_import_sub)) },
                    )
                }
            }

            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = { Text("v0.3.0 · Offline Tajik–Russian–English") },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_data_version)) },
                        supportingContent = { Text(info?.dataVersion?.ifEmpty { "—" } ?: "…") },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_stats)) },
                        supportingContent = { Text(info?.stats?.ifEmpty { "—" } ?: "…") },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    ListItem(
                        modifier = Modifier.clickable {
                            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@tajikdictionary.tj"))
                                .putExtra(Intent.EXTRA_SUBJECT, "Tajik Dictionary feedback")
                            runCatching { context.startActivity(mail) }
                        },
                        headlineContent = { Text(stringResource(R.string.settings_report_mistake)) },
                        supportingContent = { Text(stringResource(R.string.settings_report_mistake_sub)) },
                    )
                }
            }
        }
    }
}

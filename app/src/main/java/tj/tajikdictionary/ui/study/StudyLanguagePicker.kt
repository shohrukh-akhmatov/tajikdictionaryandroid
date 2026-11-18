package tj.tajikdictionary.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language

@Composable
private fun partnerName(mode: DictionaryMode): String = when (mode.partner) {
    Language.ENGLISH -> stringResource(R.string.lang_english)
    else -> stringResource(R.string.lang_russian)
}

/**
 * Segmented "Tajik ⇄ [Russian | English]" switch for the Learn screen's word trainer card (same as iOS
 * `StudyLanguagePicker`). Drives [SettingsRepository.studyMode] (game, flashcards, placement test), never the
 * dictionary mode used by Search.
 */
@Composable
fun StudyLanguagePicker(settings: SettingsRepository, modifier: Modifier = Modifier) {
    val studyMode by settings.studyMode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.study_language)
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "${stringResource(R.string.lang_tajik)} ⇄",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                DictionaryMode.entries.forEach { item ->
                    val selected = item == studyMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(role = Role.Tab) { scope.launch { settings.setStudyMode(item) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            partnerName(item),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top-bar title inside the game: the deck [title] with a small "TJ ⇄ RU ▾" under it; tapping opens a menu to switch
 * the learning language. The caller restarts the round when [SettingsRepository.studyMode] changes (answers are
 * saved as they happen, so nothing is lost).
 */
@Composable
fun StudyLanguageTitle(title: String, settings: SettingsRepository) {
    val studyMode by settings.studyMode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val a11y = "$title, ${stringResource(R.string.study_language)}: ${partnerName(studyMode)}"
    Box {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.DropdownList) { expanded = true }
                .semantics { contentDescription = a11y }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TJ ⇄ ${studyMode.partner.tag}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                stringResource(R.string.study_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            DictionaryMode.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(partnerName(item)) },
                    trailingIcon = {
                        if (item == studyMode) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        if (item != studyMode) scope.launch { settings.setStudyMode(item) }
                    },
                )
            }
        }
    }
}

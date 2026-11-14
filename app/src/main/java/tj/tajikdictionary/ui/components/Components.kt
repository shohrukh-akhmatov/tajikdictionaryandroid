package tj.tajikdictionary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.ui.theme.LocalLanguageColors

/** Small colored chip: TJ / RU / EN. */
@Composable
fun LanguageTag(language: Language, modifier: Modifier = Modifier) {
    val color = LocalLanguageColors.current.of(language)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = language.name.lowercase() },
    ) {
        Text(
            text = language.tag,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The TJK ⇄ RUS | TJK ⇄ ENG switch at the top of the search screen. */
@Composable
fun ModeSwitcher(mode: DictionaryMode, onModeChange: (DictionaryMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = DictionaryMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == mode,
                onClick = { onModeChange(item) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(item.title) }
        }
    }
}

/** Quick-insert row for Tajik letters that are missing on Russian keyboards. */
@Composable
fun TajikLettersRow(onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TAJIK_LETTERS.forEach { letter ->
            FilledTonalButton(onClick = { onLetter(letter) }) {
                Text(letter.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

val TAJIK_LETTERS = listOf('ғ', 'ӣ', 'қ', 'ӯ', 'ҳ', 'ҷ')

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

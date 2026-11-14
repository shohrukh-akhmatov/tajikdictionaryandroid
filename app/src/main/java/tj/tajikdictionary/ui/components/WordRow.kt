package tj.tajikdictionary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.domain.model.PartOfSpeech

/** Shared row used by search results, favorites and history. */
@Composable
fun WordRow(
    language: Language,
    headword: String,
    preview: String,
    pos: PartOfSpeech? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { LanguageTag(language) },
        headlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(headword, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                pos?.let {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = if (preview.isNotEmpty()) {
            { Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        trailingContent = trailing,
    )
}

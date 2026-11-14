package tj.tajikdictionary.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.tajikdictionary.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grammar_guide_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.grammar_letters_title), style = MaterialTheme.typography.titleMedium)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val letters = listOf(
                        Triple("Ғ ғ", "gh / г", "voiced uvular fricative (like French r): ғамгин"),
                        Triple("Ӣ ӣ", "ī / и", "stressed long i at word ends: бинӣ, зиндагӣ"),
                        Triple("Қ қ", "q / к", "deep uvular k from back of throat: қалам, вақт"),
                        Triple("Ӯ ӯ", "ū / у", "rounded mid vowel: хӯрдан, кӯҳ, дӯст"),
                        Triple("Ҳ ҳ", "h / х", "soft breathy h (not guttural х): ҳафта, шаҳр"),
                        Triple("Ҷ ҷ", "j / ч", "English j in 'jam': ҷавоб, куҷо"),
                    )
                    letters.forEachIndexed { idx, (ch, fold, desc) ->
                        ListItem(
                            leadingContent = {
                                Text(ch, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            },
                            headlineContent = { Text(fold, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(desc) },
                        )
                        if (idx < letters.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            Text(stringResource(R.string.grammar_patterns_title), style = MaterialTheme.typography.titleMedium)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PatternRow("Plural: -ҳо / -он", "китоб → китобҳо (books), дӯст → дӯстон (friends)")
                    PatternRow("Ezafe: -и", "хонаи калон (big house), китоби ман (my book)")
                    PatternRow("Object marker: -ро", "Дарро пӯшед. (Close the door.)")
                    PatternRow("Present tense: ме- + stem + ending", "рафтан → меравам (I go / am going)")
                }
            }
        }
    }
}

@Composable
private fun PatternRow(title: String, example: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        Text(example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

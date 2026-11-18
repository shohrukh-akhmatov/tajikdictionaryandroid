package tj.tajikdictionary.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tj.tajikdictionary.R
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarGuideScreen(onBack: () -> Unit) {
    val transparentRow = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(R.string.grammar_guide_title), fontWeight = FontWeight.Bold) },
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
            SectionHeader(title = stringResource(R.string.grammar_letters_title))
            AppCard {
                val letters = listOf(
                    Triple("Ғ ғ", "gh / г", stringResource(R.string.grammar_letter_gh_desc)),
                    Triple("Ӣ ӣ", "ī / и", stringResource(R.string.grammar_letter_i_desc)),
                    Triple("Қ қ", "q / к", stringResource(R.string.grammar_letter_q_desc)),
                    Triple("Ӯ ӯ", "ū / у", stringResource(R.string.grammar_letter_u_desc)),
                    Triple("Ҳ ҳ", "h / х", stringResource(R.string.grammar_letter_h_desc)),
                    Triple("Ҷ ҷ", "j / ч", stringResource(R.string.grammar_letter_j_desc)),
                )
                letters.forEachIndexed { idx, (ch, fold, desc) ->
                    ListItem(
                        colors = transparentRow,
                        leadingContent = {
                            Text(
                                ch,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        headlineContent = { Text(fold, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(desc) },
                    )
                    if (idx < letters.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.grammar_patterns_title))
            AppCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PatternRow(
                        stringResource(R.string.grammar_pat_plural_title),
                        stringResource(R.string.grammar_pat_plural_ex),
                    )
                    PatternRow(
                        stringResource(R.string.grammar_pat_ezafe_title),
                        stringResource(R.string.grammar_pat_ezafe_ex),
                    )
                    PatternRow(
                        stringResource(R.string.grammar_pat_obj_title),
                        stringResource(R.string.grammar_pat_obj_ex),
                    )
                    PatternRow(
                        stringResource(R.string.grammar_pat_pres_title),
                        stringResource(R.string.grammar_pat_pres_ex),
                    )
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

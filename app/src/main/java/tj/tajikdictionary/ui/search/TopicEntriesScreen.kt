package tj.tajikdictionary.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.ui.components.WordRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicEntriesScreen(
    topic: String,
    dictionary: DictionaryRepository,
    settings: SettingsRepository,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
) {
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val entries by produceState<List<SearchResult>>(initialValue = emptyList(), topic, mode) {
        value = dictionary.entriesByTopic(topic, mode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic.replaceFirstChar { it.uppercase() }) },
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(entries, key = { it.id }) { row ->
                WordRow(
                    language = row.language,
                    headword = row.headword,
                    preview = row.preview,
                    pos = row.pos,
                    onClick = { onEntryClick(row.id) },
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

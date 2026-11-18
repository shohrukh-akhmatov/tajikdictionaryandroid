package tj.tajikdictionary.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.R
import tj.tajikdictionary.data.dictionary.DictionaryRepository
import tj.tajikdictionary.data.settings.SettingsRepository
import tj.tajikdictionary.domain.model.DictionaryMode
import tj.tajikdictionary.domain.model.SearchResult
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.WordRow
import tj.tajikdictionary.ui.components.localizedTopicName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicEntriesScreen(
    topic: String,
    dictionary: DictionaryRepository,
    settings: SettingsRepository,
    onBack: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onStudyTopic: (String) -> Unit,
) {
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = DictionaryMode.TJ_RU)
    val entries by produceState<List<SearchResult>>(initialValue = emptyList(), topic, mode) {
        value = dictionary.entriesByTopic(topic, mode)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(localizedTopicName(topic), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { onStudyTopic(topic) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.study_action))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (entries.isNotEmpty()) {
                item(key = "topic_card") {
                    AppCard {
                        entries.forEachIndexed { idx, row ->
                            WordRow(
                                language = row.language,
                                headword = row.headword,
                                preview = row.preview,
                                pos = row.pos,
                                onClick = { onEntryClick(row.id) },
                            )
                            if (idx < entries.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package tj.tajikdictionary.ui.favorites

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.ui.components.EmptyState
import tj.tajikdictionary.ui.components.WordRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    userData: UserDataRepository,
    onEntryClick: (Long) -> Unit,
    onStudyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites by userData.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_saved)) },
                actions = {
                    if (favorites.isNotEmpty()) {
                        TextButton(onClick = onStudyClick) {
                            Icon(Icons.Filled.School, contentDescription = null)
                            Text(" ${stringResource(R.string.study_action)}")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.StarBorder,
                title = stringResource(R.string.saved_empty_title),
                message = stringResource(R.string.saved_empty_message),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(favorites, key = { it.entryId }) { item ->
                    WordRow(
                        language = Language.fromCode(item.lang),
                        headword = item.headword,
                        preview = item.preview,
                        onClick = { onEntryClick(item.entryId) },
                        trailing = {
                            IconButton(onClick = { scope.launch { userData.removeFavorite(item.entryId) } }) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

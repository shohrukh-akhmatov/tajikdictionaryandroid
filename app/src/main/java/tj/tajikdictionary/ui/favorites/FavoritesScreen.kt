package tj.tajikdictionary.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tj.tajikdictionary.R
import tj.tajikdictionary.data.user.UserDataRepository
import tj.tajikdictionary.domain.model.Language
import tj.tajikdictionary.ui.components.AppCard
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.tab_saved), fontWeight = FontWeight.Bold)
                        if (favorites.isNotEmpty()) {
                            Text(
                                stringResource(R.string.saved_count_subtitle, favorites.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (favorites.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onStudyClick,
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
        if (favorites.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.StarBorder,
                title = stringResource(R.string.saved_empty_title),
                message = stringResource(R.string.saved_empty_message),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "saved_card") {
                    AppCard {
                        favorites.forEachIndexed { idx, item ->
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
                            if (idx < favorites.lastIndex) {
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

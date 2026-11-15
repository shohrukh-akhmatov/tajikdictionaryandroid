package tj.tajikdictionary.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tj.tajikdictionary.AppContainer
import tj.tajikdictionary.R
import tj.tajikdictionary.ui.detail.EntryDetailScreen
import tj.tajikdictionary.ui.detail.EntryDetailViewModel
import tj.tajikdictionary.ui.favorites.FavoritesScreen
import tj.tajikdictionary.ui.history.HistoryScreen
import android.net.Uri
import tj.tajikdictionary.ui.search.SearchScreen
import tj.tajikdictionary.ui.search.SearchViewModel
import tj.tajikdictionary.ui.search.TopicEntriesScreen
import tj.tajikdictionary.ui.settings.GrammarGuideScreen
import tj.tajikdictionary.ui.settings.SettingsScreen
import tj.tajikdictionary.ui.study.GameDeckSource
import tj.tajikdictionary.ui.study.LearnScreen
import tj.tajikdictionary.ui.study.StudyScreen
import tj.tajikdictionary.ui.study.WordGameScreen

private enum class TopTab(val route: String, val labelRes: Int, val icon: ImageVector) {
    SEARCH("search", R.string.tab_dictionary, Icons.AutoMirrored.Filled.MenuBook),
    LEARN("learn", R.string.tab_learn, Icons.Filled.School),
    SAVED("favorites", R.string.tab_saved, Icons.Filled.Star),
    HISTORY("history", R.string.tab_history, Icons.Filled.History),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

data class LaunchRequest(
    val query: String = "",
    val entryId: Long? = null,
    val seq: Long = 0L,
)

@Composable
fun TajikDictionaryRoot(
    container: AppContainer,
    launchRequest: LaunchRequest = LaunchRequest(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopTab.entries.any { it.route == currentRoute }

    LaunchedEffect(launchRequest.seq) {
        if (launchRequest.seq == 0L) return@LaunchedEffect
        val entryId = launchRequest.entryId
        if (entryId != null && entryId > 0L) {
            navController.navigate("entry/$entryId") { launchSingleTop = true }
        } else if (launchRequest.query.isNotBlank()) {
            navController.navigate(TopTab.SEARCH.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val openEntry: (Long) -> Unit = { id -> navController.navigate("entry/$id") }

        NavHost(
            navController = navController,
            startDestination = TopTab.SEARCH.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopTab.SEARCH.route) {
                val vm: SearchViewModel = viewModel(
                    factory = SearchViewModel.Factory(
                        dictionary = container.dictionary,
                        userData = container.userData,
                        settings = container.settings,
                        initialQuery = launchRequest.query,
                    ),
                )
                LaunchedEffect(launchRequest.seq) {
                    if (launchRequest.seq > 0L && launchRequest.query.isNotBlank()) {
                        vm.onQueryChange(launchRequest.query)
                    }
                }
                SearchScreen(
                    viewModel = vm,
                    onEntryClick = openEntry,
                    onTopicClick = { topic -> navController.navigate("topic/${Uri.encode(topic)}") },
                )
            }
            composable(
                route = "topic/{topic}",
                arguments = listOf(navArgument("topic") { type = NavType.StringType }),
            ) { entry ->
                val topic = entry.arguments?.getString("topic") ?: return@composable
                TopicEntriesScreen(
                    topic = topic,
                    dictionary = container.dictionary,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onEntryClick = openEntry,
                    onStudyTopic = { t -> navController.navigate("game?source=topic&topic=${Uri.encode(t)}") },
                )
            }
            composable(TopTab.LEARN.route) {
                LearnScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onStartGame = { source ->
                        val route = when (source) {
                            GameDeckSource.SmartMix -> "game?source=smart"
                            GameDeckSource.Saved -> "game?source=saved"
                            is GameDeckSource.Topic -> "game?source=topic&topic=${Uri.encode(source.topic)}"
                        }
                        navController.navigate(route)
                    },
                    onOpenFlashcards = { topic ->
                        val route = if (topic == null) "study" else "study?topic=${Uri.encode(topic)}"
                        navController.navigate(route)
                    },
                )
            }
            composable(TopTab.SAVED.route) {
                FavoritesScreen(
                    userData = container.userData,
                    onEntryClick = openEntry,
                    onStudyClick = { navController.navigate("game?source=saved") },
                )
            }
            composable(TopTab.HISTORY.route) {
                HistoryScreen(userData = container.userData, onEntryClick = openEntry)
            }
            composable(TopTab.SETTINGS.route) {
                SettingsScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onOpenGrammarGuide = { navController.navigate("grammar") },
                    onOpenStudy = { navController.navigate("study") },
                )
            }
            composable(
                route = "game?source={source}&topic={topic}",
                arguments = listOf(
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = "smart"
                    },
                    navArgument("topic") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val src = entry.arguments?.getString("source") ?: "smart"
                val topic = entry.arguments?.getString("topic")
                val deckSource = when {
                    src == "saved" -> GameDeckSource.Saved
                    src == "topic" && !topic.isNullOrBlank() -> GameDeckSource.Topic(topic)
                    else -> GameDeckSource.SmartMix
                }
                WordGameScreen(
                    source = deckSource,
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "study?topic={topic}",
                arguments = listOf(
                    navArgument("topic") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                StudyScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    initialTopic = entry.arguments?.getString("topic"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("grammar") {
                GrammarGuideScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "entry/{entryId}",
                arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("entryId") ?: return@composable
                val vm: EntryDetailViewModel = viewModel(
                    key = "entry_$id",
                    factory = EntryDetailViewModel.Factory(
                        entryId = id,
                        dictionary = container.dictionary,
                        userData = container.userData,
                        settings = container.settings,
                    ),
                )
                EntryDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEntryClick = openEntry,
                )
            }
        }
    }
}

package tj.tajikdictionary.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import tj.tajikdictionary.ui.search.SearchScreen
import tj.tajikdictionary.ui.search.SearchViewModel
import tj.tajikdictionary.ui.settings.SettingsScreen

private enum class TopTab(val route: String, val labelRes: Int, val icon: ImageVector) {
    SEARCH("search", R.string.tab_dictionary, Icons.AutoMirrored.Filled.MenuBook),
    SAVED("favorites", R.string.tab_saved, Icons.Filled.Star),
    HISTORY("history", R.string.tab_history, Icons.Filled.History),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun TajikDictionaryRoot(
    container: AppContainer,
    initialQuery: String = "",
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopTab.entries.any { it.route == currentRoute }

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
                        initialQuery = initialQuery,
                    ),
                )
                SearchScreen(viewModel = vm, onEntryClick = openEntry)
            }
            composable(TopTab.SAVED.route) {
                FavoritesScreen(userData = container.userData, onEntryClick = openEntry)
            }
            composable(TopTab.HISTORY.route) {
                HistoryScreen(userData = container.userData, onEntryClick = openEntry)
            }
            composable(TopTab.SETTINGS.route) {
                SettingsScreen(dictionary = container.dictionary, settings = container.settings)
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

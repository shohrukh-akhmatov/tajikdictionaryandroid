package tj.tajikdictionary.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.LocalActivity
import tj.tajikdictionary.ads.AdsManager
import tj.tajikdictionary.ads.AdPlacement
import tj.tajikdictionary.ads.PinnedBanner
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import tj.tajikdictionary.ui.onboarding.OnboardingScreen
import tj.tajikdictionary.ui.study.LearnProgressScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import tj.tajikdictionary.domain.model.LearnerProfile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tj.tajikdictionary.ui.components.KeyboardDismissRoot
import tj.tajikdictionary.ui.detail.EntryDetailScreen
import tj.tajikdictionary.ui.detail.EntryDetailViewModel
import tj.tajikdictionary.ui.favorites.FavoritesScreen
import tj.tajikdictionary.ui.history.HistoryScreen
import tj.tajikdictionary.ui.search.SearchScreen
import tj.tajikdictionary.ui.search.SearchViewModel
import tj.tajikdictionary.ui.search.TopicEntriesScreen
import tj.tajikdictionary.ui.settings.GrammarGuideScreen
import tj.tajikdictionary.ui.settings.SettingsScreen
import tj.tajikdictionary.ui.study.GameDeckSource
import tj.tajikdictionary.ui.study.LearnScreen
import tj.tajikdictionary.ui.study.PlacementTestScreen
import tj.tajikdictionary.ui.study.StudyScreen
import tj.tajikdictionary.ui.study.VocabularyListScreen
import tj.tajikdictionary.ui.study.WordGameScreen

private enum class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    SEARCH("search", R.string.tab_dictionary, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    LEARN("learn", R.string.tab_learn, Icons.Filled.School, Icons.Outlined.School),
    SAVED("favorites", R.string.tab_saved, Icons.Filled.Star, Icons.Outlined.StarBorder),
    HISTORY("history", R.string.tab_history, Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
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

    // Decided once per launch (iOS `RootView.checkOnboarding`): legacy settings or any saved history,
    // favorites or flashcard progress mean an existing user, who never sees onboarding.
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var onboardingChecked by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (onboardingChecked) return@LaunchedEffect
        val settings = container.settings
        if (!settings.hasCompletedOnboarding.first()) {
            val userData = container.userData
            val hasUsage = userData.history.first().isNotEmpty() ||
                userData.favorites.first().isNotEmpty() ||
                userData.flashcards.first().isNotEmpty()
            if (hasUsage) {
                settings.setHasCompletedOnboarding(true)
            } else {
                // Explicit false: choices made during the flow write legacy keys, which must not end it early.
                settings.setHasCompletedOnboarding(false)
                showOnboarding = true
            }
        }
        onboardingChecked = true
    }

    // Ads (consent first) start only once onboarding — and the placement test, if chosen — is over.
    val activity = LocalActivity.current
    var adsWaitForPlacement by rememberSaveable { mutableStateOf(false) }
    var placementSeen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(onboardingChecked, showOnboarding, adsWaitForPlacement, currentRoute) {
        if (!onboardingChecked || showOnboarding) return@LaunchedEffect
        if (adsWaitForPlacement) {
            if (currentRoute == "placement") placementSeen = true
            if (!placementSeen || currentRoute == "placement") return@LaunchedEffect
            adsWaitForPlacement = false
        }
        activity?.let { AdsManager.start(it) }
    }
    val activeProfile by container.settings.activeProfile.collectAsStateWithLifecycle(initialValue = LearnerProfile.defaultProfile())
    // A pinned banner on every screen except Settings (and off for child profiles); the word detail screen has its own ad unit.
    // Hidden while the keyboard is up, so it never sits between the text field and the keys.
    val bannerPlacement = when {
        currentRoute == null || showOnboarding || currentRoute == TopTab.SETTINGS.route || activeProfile.isChild -> null
        currentRoute.startsWith("entry/") -> AdPlacement.ENTRY_BANNER
        else -> AdPlacement.BANNER
    }
    val bannerVisible = bannerPlacement != null && !isImeVisible()

    Box(Modifier.fillMaxSize()) {
    KeyboardDismissRoot { dismissModifier ->
    Scaffold(
        modifier = dismissModifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showBottomBar || bannerVisible) {
                Column {
                    if (bannerVisible && bannerPlacement != null) PinnedBanner(bannerPlacement)
                    if (!showBottomBar) {
                        // No navigation bar on this screen: keep the banner above the system nav bar.
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        return@Column
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 0.dp,
                    ) {
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
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val openEntry: (Long) -> Unit = { id -> navController.navigate("entry/$id") }
        val openTopic: (String) -> Unit = { topic -> navController.navigate("topic/${Uri.encode(topic)}") }

        NavHost(
            navController = navController,
            startDestination = TopTab.SEARCH.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
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
                    onTopicClick = openTopic,
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
                    onStudyTopic = { t ->
                        scope.launch {
                            container.settings.setStudyMode(container.settings.mode.first())
                            navController.navigate("game?source=topic&topic=${Uri.encode(t)}")
                        }
                    },
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
                            GameDeckSource.DueReviews -> "game?source=due"
                            GameDeckSource.WeakWords -> "game?source=weak"
                            GameDeckSource.Saved -> "game?source=saved"
                            is GameDeckSource.Topic -> "game?source=topic&topic=${Uri.encode(source.topic)}"
                        }
                        navController.navigate(route)
                    },
                    onOpenFlashcards = { topic ->
                        val route = if (topic == null) "study" else "study?topic=${Uri.encode(topic)}"
                        navController.navigate(route)
                    },
                    onOpenPlacement = { navController.navigate("placement") },
                    onOpenProgress = { navController.navigate("progress") },
                )
            }
            composable("progress") {
                LearnProgressScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onOpenPlacement = { navController.navigate("placement") },
                    onOpenVocabulary = { navController.navigate("vocabulary") },
                )
            }
            composable(TopTab.SAVED.route) {
                FavoritesScreen(
                    userData = container.userData,
                    onEntryClick = openEntry,
                    onStudyClick = {
                        scope.launch {
                            container.settings.setStudyMode(container.settings.mode.first())
                            navController.navigate("game?source=saved")
                        }
                    },
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
                    onOpenPlacement = { navController.navigate("placement") },
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
                    src == "due" -> GameDeckSource.DueReviews
                    src == "weak" -> GameDeckSource.WeakWords
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
            composable("placement") {
                PlacementTestScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("vocabulary") {
                VocabularyListScreen(
                    dictionary = container.dictionary,
                    userData = container.userData,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onEntryClick = openEntry,
                    onDrillWeak = { navController.navigate("game?source=weak") },
                )
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
                    onTopicClick = openTopic,
                )
            }
        }
    }
    }

    if (showOnboarding) {
        OnboardingScreen(
            settings = container.settings,
            onFinish = { startPlacement ->
                scope.launch { container.settings.setHasCompletedOnboarding(true) }
                if (startPlacement) {
                    placementSeen = false
                    adsWaitForPlacement = true
                }
                showOnboarding = false
                if (startPlacement) {
                    navController.navigate(TopTab.LEARN.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    navController.navigate("placement")
                }
            },
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun isImeVisible(): Boolean = WindowInsets.isImeVisible

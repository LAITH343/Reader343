package com.reader343.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader343.ui.components.BottomBarVisibility
import com.reader343.ui.components.LocalBottomBarVisibility
import com.reader343.ui.components.Motion
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.library.HomeRoute
import com.reader343.ui.library.LibraryRoute
import com.reader343.ui.reader.ReaderRoute
import com.reader343.ui.settings.SettingsRoute
import com.reader343.ui.stats.StatsRoute
import com.reader343.ui.theme.appColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}?notes={notes}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_NOTES = "notes"

    fun reader(bookId: Long, notes: Boolean = false) = "reader/$bookId?notes=$notes"
}

@Composable
fun AppNav(continueRequests: Flow<Long?> = emptyFlow()) {
    val navController = rememberNavController()
    val reduced = reducedMotion()
    val bottomBar = remember { BottomBarVisibility() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route } == true
    }

    CompositionLocalProvider(LocalBottomBarVisibility provides bottomBar) {
        Scaffold(
            containerColor = MaterialTheme.appColors.bg,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (currentTab != null && !bottomBar.hidden) {
                    AppBottomBar(
                        selected = currentTab,
                        onSelect = { navController.navigateToTab(it) },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { Motion.fadeEnter(reduced) },
                exitTransition = { Motion.fadeExit(reduced) },
                popEnterTransition = { Motion.fadeEnter(reduced) },
                popExitTransition = { Motion.fadeExit(reduced) },
            ) {
                composable(Routes.HOME) { entry ->
                    HomeRoute(
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.reader(it, notes = true)) },
                        onOpenLibrary = { navController.tabFrom(entry, TopLevelTab.Library) },
                        onOpenSettings = { navController.tabFrom(entry, TopLevelTab.Settings) },
                        onSetGoal = { navController.tabFrom(entry, TopLevelTab.Settings) },
                    )
                }
                composable(Routes.LIBRARY) { entry ->
                    LibraryRoute(
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.reader(it, notes = true)) },
                    )
                }
                composable(
                    route = Routes.READER,
                    arguments = listOf(
                        navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType },
                        navArgument(Routes.ARG_NOTES) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    ReaderRoute(onBack = { navController.popFrom(entry) })
                }
                composable(Routes.STATS) { entry ->
                    StatsRoute(
                        onOpenLibrary = { navController.tabFrom(entry, TopLevelTab.Library) },
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute()
                }
            }
        }
    }
    LaunchedEffect(navController, continueRequests) {
        continueRequests.collect { bookId ->
            if (bookId == null) {
                navController.navigateToTab(TopLevelTab.Home)
            } else {
                navController.navigate(Routes.reader(bookId)) { popUpTo(Routes.HOME) }
            }
        }
    }
}

private val NavBackStackEntry.isResumed: Boolean
    get() = lifecycle.currentState == Lifecycle.State.RESUMED

private fun NavController.popFrom(entry: NavBackStackEntry) {
    if (entry.isResumed) popBackStack()
}

private fun NavController.navigateFrom(entry: NavBackStackEntry, route: String) {
    if (entry.isResumed) navigate(route)
}

private fun NavController.tabFrom(entry: NavBackStackEntry, tab: TopLevelTab) {
    if (entry.isResumed) navigateToTab(tab)
}

private fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

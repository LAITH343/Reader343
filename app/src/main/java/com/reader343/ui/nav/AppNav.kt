package com.reader343.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.reader343.ui.detail.BookDetailRoute
import com.reader343.ui.library.HomeRoute
import com.reader343.ui.library.LibraryRoute
import com.reader343.ui.notes.NotesRoute
import com.reader343.ui.reader.ReaderRoute
import com.reader343.ui.settings.SettingsRoute
import com.reader343.ui.stats.StatsRoute
import com.reader343.ui.theme.appColors
import com.reader343.ui.update.UpdateRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ReaderRequest(val bookId: Long?, val page: Int = -1)

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}?page={page}"
    const val NOTES = "notes/{bookId}"
    const val BOOK = "book/{bookId}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val UPDATE = "update"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_PAGE = "page"
    const val RESULT_PAGE = "resultPage"

    fun reader(bookId: Long, page: Int = -1) = "reader/$bookId?page=$page"

    fun notes(bookId: Long) = "notes/$bookId"

    fun book(bookId: Long) = "book/$bookId"
}

@Composable
fun AppNav(
    continueRequests: Flow<ReaderRequest> = emptyFlow(),
    updateRequests: Flow<Unit> = emptyFlow(),
) {
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
                        onOpenInfo = { navController.navigateFrom(entry, Routes.book(it)) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.notes(it)) },
                        onOpenLibrary = { navController.tabFrom(entry, TopLevelTab.Library) },
                        onOpenUpdate = { navController.navigateFrom(entry, Routes.UPDATE) },
                    )
                }
                composable(Routes.LIBRARY) { entry ->
                    LibraryRoute(
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                        onOpenInfo = { navController.navigateFrom(entry, Routes.book(it)) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.notes(it)) },
                    )
                }
                composable(
                    route = Routes.BOOK,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) { entry ->
                    BookDetailRoute(
                        onBack = { navController.popFrom(entry) },
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.notes(it)) },
                    )
                }
                composable(
                    route = Routes.READER,
                    arguments = listOf(
                        navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType },
                        navArgument(Routes.ARG_PAGE) {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                ) { entry ->
                    val bookId = entry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: 0L
                    val jumpRequest by entry.savedStateHandle.getStateFlow(Routes.RESULT_PAGE, -1).collectAsState()
                    ReaderRoute(
                        onBack = { navController.popFrom(entry) },
                        onOpenNotes = { navController.navigateFrom(entry, Routes.notes(bookId)) },
                        jumpRequest = jumpRequest,
                        onJumpHandled = { entry.savedStateHandle[Routes.RESULT_PAGE] = -1 },
                    )
                }
                composable(
                    route = Routes.NOTES,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) { entry ->
                    val bookId = entry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: 0L
                    NotesRoute(
                        onBack = { navController.popFrom(entry) },
                        onGoToPage = { page -> navController.goToPageFrom(entry, bookId, page) },
                    )
                }
                composable(Routes.STATS) { entry ->
                    StatsRoute(
                        onOpenLibrary = { navController.tabFrom(entry, TopLevelTab.Library) },
                        onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                    )
                }
                composable(Routes.SETTINGS) { entry ->
                    SettingsRoute(
                        onOpenUpdate = { navController.navigateFrom(entry, Routes.UPDATE) },
                    )
                }
                composable(Routes.UPDATE) { entry ->
                    UpdateRoute(onBack = { navController.popFrom(entry) })
                }
            }
        }
    }
    LaunchedEffect(navController, continueRequests) {
        continueRequests.collect { request ->
            val bookId = request.bookId
            val current = navController.currentBackStackEntry
            when {
                bookId == null -> navController.navigateToTab(TopLevelTab.Home)
                current?.destination?.route == Routes.READER &&
                    current.arguments?.getLong(Routes.ARG_BOOK_ID) == bookId -> {
                    if (request.page >= 0) current.savedStateHandle[Routes.RESULT_PAGE] = request.page
                }
                else -> navController.navigate(Routes.reader(bookId, request.page)) { popUpTo(Routes.HOME) }
            }
        }
    }
    LaunchedEffect(navController, updateRequests) {
        updateRequests.collect {
            navController.navigate(Routes.UPDATE) { launchSingleTop = true }
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

private fun NavController.goToPageFrom(entry: NavBackStackEntry, bookId: Long, page: Int) {
    if (!entry.isResumed) return
    val previous = previousBackStackEntry
    if (previous?.destination?.route == Routes.READER) {
        previous.savedStateHandle[Routes.RESULT_PAGE] = page
        popBackStack()
    } else {
        navigate(Routes.reader(bookId, page)) {
            popUpTo(Routes.NOTES) { inclusive = true }
        }
    }
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

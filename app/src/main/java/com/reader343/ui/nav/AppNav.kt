package com.reader343.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reader343.ui.components.Motion
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.library.LibraryRoute
import com.reader343.ui.reader.ReaderRoute
import com.reader343.ui.stats.StatsRoute

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
    const val STATS = "stats"
    const val ARG_BOOK_ID = "bookId"

    fun reader(bookId: Long) = "reader/$bookId"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val reduced = reducedMotion()
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        enterTransition = { Motion.fadeEnter(reduced) },
        exitTransition = { Motion.fadeExit(reduced) },
        popEnterTransition = { Motion.fadeEnter(reduced) },
        popExitTransition = { Motion.fadeExit(reduced) },
    ) {
        composable(Routes.LIBRARY) { entry ->
            LibraryRoute(
                onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
                onOpenStats = { navController.navigateFrom(entry, Routes.STATS) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
        ) { entry ->
            ReaderRoute(onBack = { navController.popFrom(entry) })
        }
        composable(Routes.STATS) { entry ->
            StatsRoute(
                onBack = { navController.popFrom(entry) },
                onOpenBook = { navController.navigateFrom(entry, Routes.reader(it)) },
            )
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

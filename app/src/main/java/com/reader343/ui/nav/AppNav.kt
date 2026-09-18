package com.reader343.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryRoute(
                onOpenBook = { navController.navigate(Routes.reader(it)) },
                onOpenStats = { navController.navigate(Routes.STATS) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
        ) {
            ReaderRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsRoute(onBack = { navController.popBackStack() })
        }
    }
}

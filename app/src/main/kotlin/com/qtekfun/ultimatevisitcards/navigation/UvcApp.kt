package com.qtekfun.ultimatevisitcards.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qtekfun.ultimatevisitcards.ui.capture.CaptureScreen
import com.qtekfun.ultimatevisitcards.ui.cards.CardsScreen
import com.qtekfun.ultimatevisitcards.ui.companies.CompaniesScreen
import com.qtekfun.ultimatevisitcards.ui.events.EventsScreen
import com.qtekfun.ultimatevisitcards.ui.queue.ReviewQueueScreen
import com.qtekfun.ultimatevisitcards.ui.quickscan.QuickScanScreen
import com.qtekfun.ultimatevisitcards.ui.review.ReviewExit
import com.qtekfun.ultimatevisitcards.ui.review.ReviewScreen
import com.qtekfun.ultimatevisitcards.ui.review.ReviewViewModel
import com.qtekfun.ultimatevisitcards.ui.sectors.SectorsScreen
import com.qtekfun.ultimatevisitcards.ui.settings.SettingsScreen

/** Routes outside the bottom bar: they open over it (docs/03-pantallas-navegacion.md). */
object Routes {
    const val CAPTURE = "capture"
    const val QUICK_SCAN = "quick-scan"
    const val QUEUE = "queue"
    const val REVIEW = "review/{${ReviewViewModel.ARG_CARD_ID}}?${ReviewViewModel.ARG_FROM_QUEUE}={${ReviewViewModel.ARG_FROM_QUEUE}}"

    fun review(cardId: String, fromQueue: Boolean = false) = "review/$cardId?${ReviewViewModel.ARG_FROM_QUEUE}=$fromQueue"
}

/** Root scaffold: bottom navigation over the five top-level sections. */
@Composable
fun UvcApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) BottomBar(navController, currentDestination)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.CARDS.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(TopLevelDestination.CARDS.route) {
                CardsScreen(
                    onAddCard = { navController.navigate(Routes.CAPTURE) },
                    onQuickScan = { navController.navigate(Routes.QUICK_SCAN) },
                    onPending = { navController.navigate(Routes.QUEUE) }
                )
            }
            composable(TopLevelDestination.EVENTS.route) { EventsScreen() }
            composable(TopLevelDestination.COMPANIES.route) { CompaniesScreen() }
            composable(TopLevelDestination.SECTORS.route) { SectorsScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onClose = { navController.popBackStack() },
                    // Normal mode: front → back? → review (docs/03).
                    onSaved = { cardId -> navController.navigate(Routes.review(cardId)) { popUpTo(Routes.CAPTURE) { inclusive = true } } }
                )
            }
            composable(Routes.QUICK_SCAN) { QuickScanScreen(onFinished = { navController.popBackStack() }) }
            composable(Routes.QUEUE) {
                ReviewQueueScreen(
                    onReview = { cardId -> navController.navigate(Routes.review(cardId, fromQueue = true)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Routes.REVIEW,
                arguments = listOf(
                    navArgument(ReviewViewModel.ARG_CARD_ID) { type = NavType.StringType },
                    navArgument(ReviewViewModel.ARG_FROM_QUEUE) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                ReviewScreen(
                    onBack = { navController.popBackStack() },
                    onExit = { exit ->
                        when (exit) {
                            ReviewExit.Done -> navController.popBackStack()
                            ReviewExit.ScanNext -> navController.navigate(Routes.CAPTURE) { popUpTo(Routes.REVIEW) { inclusive = true } }
                            is ReviewExit.ReviewNext -> navController.navigate(Routes.review(exit.cardId, fromQueue = true)) {
                                popUpTo(Routes.REVIEW) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentDestination: NavDestination?) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.label)
            NavigationBarItem(
                selected = currentDestination.isOn(destination),
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // The label already names the item: the icon would only repeat it to TalkBack.
                icon = { Icon(destination.iconPainter(), contentDescription = null) },
                label = { Text(label) }
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean = this?.hierarchy?.any { it.route == destination.route } == true

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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qtekfun.ultimatevisitcards.ui.cards.CardsScreen
import com.qtekfun.ultimatevisitcards.ui.companies.CompaniesScreen
import com.qtekfun.ultimatevisitcards.ui.events.EventsScreen
import com.qtekfun.ultimatevisitcards.ui.sectors.SectorsScreen
import com.qtekfun.ultimatevisitcards.ui.settings.SettingsScreen

/** Root scaffold: bottom navigation over the five top-level sections. */
@Composable
fun UvcApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val label = stringResource(destination.label)
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // The label already names the item: the icon would only repeat it to TalkBack.
                        icon = { Icon(destination.iconPainter(), contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.CARDS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.CARDS.route) { CardsScreen() }
            composable(TopLevelDestination.EVENTS.route) { EventsScreen() }
            composable(TopLevelDestination.COMPANIES.route) { CompaniesScreen() }
            composable(TopLevelDestination.SECTORS.route) { SectorsScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

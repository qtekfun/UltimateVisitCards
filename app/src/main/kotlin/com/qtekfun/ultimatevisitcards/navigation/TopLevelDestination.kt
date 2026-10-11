package com.qtekfun.ultimatevisitcards.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.qtekfun.ultimatevisitcards.R

/** Destinations of the bottom bar, in display order (docs/03-pantallas-navegacion.md). */
enum class TopLevelDestination(val route: String, @StringRes val label: Int) {
    CARDS("cards", R.string.nav_cards),
    EVENTS("events", R.string.nav_events),
    COMPANIES("companies", R.string.nav_companies),
    SECTORS("sectors", R.string.nav_sectors),
    SETTINGS("settings", R.string.nav_settings),
}

@Suppress("DEPRECATION") // Icons.Filled.List: the auto-mirrored variant is not in material-icons-core.
@Composable
fun TopLevelDestination.iconPainter(): Painter = when (this) {
    TopLevelDestination.CARDS -> rememberVectorPainter(Icons.Filled.AccountBox)
    TopLevelDestination.EVENTS -> rememberVectorPainter(Icons.Filled.DateRange)
    TopLevelDestination.COMPANIES -> painterResource(R.drawable.ic_company)
    TopLevelDestination.SECTORS -> rememberVectorPainter(Icons.Filled.List)
    TopLevelDestination.SETTINGS -> rememberVectorPainter(Icons.Filled.Settings)
}

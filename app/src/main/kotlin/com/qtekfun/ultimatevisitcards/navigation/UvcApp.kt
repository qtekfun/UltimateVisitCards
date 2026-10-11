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
import com.qtekfun.ultimatevisitcards.ui.about.AboutScreen
import com.qtekfun.ultimatevisitcards.ui.capture.CaptureScreen
import com.qtekfun.ultimatevisitcards.ui.cards.CardsActions
import com.qtekfun.ultimatevisitcards.ui.cards.CardsScreen
import com.qtekfun.ultimatevisitcards.ui.cards.CardsViewModel
import com.qtekfun.ultimatevisitcards.ui.companies.CompaniesScreen
import com.qtekfun.ultimatevisitcards.ui.companies.CompanyDetailScreen
import com.qtekfun.ultimatevisitcards.ui.companies.CompanyDetailViewModel
import com.qtekfun.ultimatevisitcards.ui.contact.ContactActions
import com.qtekfun.ultimatevisitcards.ui.contact.ContactDetailScreen
import com.qtekfun.ultimatevisitcards.ui.contact.ContactDetailViewModel
import com.qtekfun.ultimatevisitcards.ui.contact.ContactEditScreen
import com.qtekfun.ultimatevisitcards.ui.events.EventDetailScreen
import com.qtekfun.ultimatevisitcards.ui.events.EventDetailViewModel
import com.qtekfun.ultimatevisitcards.ui.events.EventsScreen
import com.qtekfun.ultimatevisitcards.ui.duplicates.DuplicatesScreen
import com.qtekfun.ultimatevisitcards.ui.merge.MergeScreen
import com.qtekfun.ultimatevisitcards.ui.merge.MergeViewModel
import com.qtekfun.ultimatevisitcards.ui.queue.ReviewQueueScreen
import com.qtekfun.ultimatevisitcards.ui.quickscan.QuickScanScreen
import com.qtekfun.ultimatevisitcards.ui.review.ReviewExit
import com.qtekfun.ultimatevisitcards.ui.review.ReviewScreen
import com.qtekfun.ultimatevisitcards.ui.review.ReviewViewModel
import com.qtekfun.ultimatevisitcards.ui.sectors.SectorsScreen
import com.qtekfun.ultimatevisitcards.ui.settings.SettingsScreen

/** Routes outside the bottom bar: they open over it (docs/03-pantallas-navegacion.md). */
object Routes {
    /** Capture; with a contact id the new card goes to that contact's history (docs/06 §5). */
    const val CAPTURE = "capture?${ReviewViewModel.ARG_TARGET_CONTACT}={${ReviewViewModel.ARG_TARGET_CONTACT}}"

    fun capture(contactId: String? = null) = "capture?${ReviewViewModel.ARG_TARGET_CONTACT}=${contactId.orEmpty()}"
    const val QUICK_SCAN = "quick-scan"
    const val QUEUE = "queue"
    const val REVIEW = "review/{${ReviewViewModel.ARG_CARD_ID}}?${ReviewViewModel.ARG_FROM_QUEUE}={${ReviewViewModel.ARG_FROM_QUEUE}}" +
        "&${ReviewViewModel.ARG_TARGET_CONTACT}={${ReviewViewModel.ARG_TARGET_CONTACT}}"

    fun review(cardId: String, fromQueue: Boolean = false, contactId: String? = null) =
        "review/$cardId?${ReviewViewModel.ARG_FROM_QUEUE}=$fromQueue&${ReviewViewModel.ARG_TARGET_CONTACT}=${contactId.orEmpty()}"

    const val CONTACT_EDIT = "contact/{${ContactDetailViewModel.ARG_CONTACT_ID}}/edit"

    fun contactEdit(contactId: String) = "contact/$contactId/edit"

    const val MERGE = "merge/{${MergeViewModel.ARG_KEEP_ID}}?${MergeViewModel.ARG_OTHER_ID}={${MergeViewModel.ARG_OTHER_ID}}"

    fun merge(keepId: String, otherId: String? = null) = "merge/$keepId?${MergeViewModel.ARG_OTHER_ID}=${otherId.orEmpty()}"

    const val DUPLICATES = "duplicates"

    const val ABOUT = "about"

    const val CONTACT = "contact/{${ContactDetailViewModel.ARG_CONTACT_ID}}"

    fun contact(contactId: String) = "contact/$contactId"

    /** The Cards tab with an optional starting search (global search from an event). */
    const val CARDS_WITH_QUERY = "cards?${CardsViewModel.ARG_QUERY}={${CardsViewModel.ARG_QUERY}}"

    const val EVENT = "event/{${EventDetailViewModel.ARG_EVENT_ID}}"

    fun event(eventId: String) = "event/$eventId"

    const val COMPANY = "company/{${CompanyDetailViewModel.ARG_COMPANY_ID}}"

    fun company(companyId: String) = "company/$companyId"

    fun cards(query: String) = "cards?${CardsViewModel.ARG_QUERY}=${android.net.Uri.encode(query)}"
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
            startDestination = Routes.CARDS_WITH_QUERY,
            modifier = Modifier.padding(padding)
        ) {
            composable(
                Routes.CARDS_WITH_QUERY,
                arguments = listOf(
                    navArgument(CardsViewModel.ARG_QUERY) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                CardsScreen(
                    CardsActions(
                        onAddCard = { navController.navigate(Routes.capture()) },
                        onQuickScan = { navController.navigate(Routes.QUICK_SCAN) },
                        onPending = { navController.navigate(Routes.QUEUE) },
                        onOpenContact = { navController.navigate(Routes.contact(it)) }
                    )
                )
            }
            composable(Routes.CONTACT, arguments = listOf(navArgument(ContactDetailViewModel.ARG_CONTACT_ID) { type = NavType.StringType })) {
                ContactDetailScreen(
                    ContactActions(
                        onBack = { navController.popBackStack() },
                        onOpenCompany = { navController.navigate(Routes.company(it)) },
                        onEdit = { navController.navigate(Routes.contactEdit(it)) },
                        onMerge = { navController.navigate(Routes.merge(it)) },
                        onAddCard = { navController.navigate(Routes.capture(it)) }
                    )
                )
            }
            composable(Routes.CONTACT_EDIT, arguments = listOf(navArgument(ContactDetailViewModel.ARG_CONTACT_ID) { type = NavType.StringType })) {
                ContactEditScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.MERGE,
                arguments = listOf(
                    navArgument(MergeViewModel.ARG_KEEP_ID) { type = NavType.StringType },
                    navArgument(MergeViewModel.ARG_OTHER_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                MergeScreen(onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.EVENTS.route) { EventsScreen(onOpenEvent = { navController.navigate(Routes.event(it)) }) }
            composable(TopLevelDestination.COMPANIES.route) { CompaniesScreen(onOpenCompany = { navController.navigate(Routes.company(it)) }) }
            composable(Routes.EVENT, arguments = listOf(navArgument(EventDetailViewModel.ARG_EVENT_ID) { type = NavType.StringType })) {
                EventDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenContact = { navController.navigate(Routes.contact(it)) },
                    // «Only in this event» removed: the same text as a global search in Cards.
                    onGlobalSearch = { text ->
                        navController.navigate(Routes.cards(text)) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.COMPANY, arguments = listOf(navArgument(CompanyDetailViewModel.ARG_COMPANY_ID) { type = NavType.StringType })) {
                CompanyDetailScreen(onBack = { navController.popBackStack() }, onOpenContact = { navController.navigate(Routes.contact(it)) })
            }
            composable(TopLevelDestination.SECTORS.route) { SectorsScreen() }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(onDuplicates = { navController.navigate(Routes.DUPLICATES) }, onAbout = { navController.navigate(Routes.ABOUT) })
            }
            composable(Routes.DUPLICATES) {
                DuplicatesScreen(onBack = { navController.popBackStack() }, onMerge = { keepId, otherId -> navController.navigate(Routes.merge(keepId, otherId)) })
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
            composable(
                Routes.CAPTURE,
                arguments = listOf(
                    navArgument(ReviewViewModel.ARG_TARGET_CONTACT) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val contactId = entry.arguments?.getString(ReviewViewModel.ARG_TARGET_CONTACT)?.takeIf { it.isNotEmpty() }
                CaptureScreen(
                    onClose = { navController.popBackStack() },
                    // Normal mode: front → back? → review (docs/03); for a contact, the review compares with it.
                    onSaved = { cardId ->
                        navController.navigate(Routes.review(cardId, contactId = contactId)) { popUpTo(Routes.CAPTURE) { inclusive = true } }
                    }
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
                    },
                    navArgument(ReviewViewModel.ARG_TARGET_CONTACT) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                ReviewScreen(
                    onBack = { navController.popBackStack() },
                    onExit = { exit ->
                        when (exit) {
                            ReviewExit.Done -> navController.popBackStack()
                            ReviewExit.ScanNext -> navController.navigate(Routes.capture()) { popUpTo(Routes.REVIEW) { inclusive = true } }
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

// Routes may carry optional arguments («cards?q={q}»): compare the path only.
private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route?.substringBefore('?') == destination.route } == true

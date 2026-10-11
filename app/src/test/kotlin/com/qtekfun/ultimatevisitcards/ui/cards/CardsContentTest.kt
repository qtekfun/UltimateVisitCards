package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.ContactListItem
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.model.Stats
import com.qtekfun.ultimatevisitcards.settings.CardsView
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardsContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val smm = EventSummary(Event("e1", "SMM 2026", createdAt = 0), 2)

    private fun content(
        items: List<CardItem> = emptyList(),
        filter: ContactFilter = ContactFilter(),
        contacts: Int = items.size,
        pending: Int = 0,
        view: CardsView = CardsView.GRID
    ) = CardsUiState.Content(items, filter, Stats(contacts, 1, 1), pending, view, FilterOptions(events = listOf(smm)))

    private fun item(first: String, last: String) =
        CardItem(ContactListItem(Contact(first + last, first, last, createdAt = 0, updatedAt = 0), "ABC Marine", "Spain", null), null)

    @Test
    fun emptyWithoutContactsAndNoResultsWithFilters() {
        compose.setContent { CardsContent(content(), CardsActions()) }
        compose.onNodeWithText(string(R.string.cards_empty)).assertExists()
    }

    @Test
    fun aSearchWithoutResultsSaysSo() {
        compose.setContent { CardsContent(content(filter = ContactFilter(text = "zzz"), contacts = 5), CardsActions()) }
        compose.onNodeWithText(string(R.string.cards_no_results)).assertExists()
    }

    @Test
    fun showsStatsPendingAndContacts() {
        var opened: String? = null
        var queue = false
        compose.setContent {
            CardsContent(content(items = listOf(item("Ana", "Ruiz")), pending = 3), CardsActions(onPending = { queue = true }, onOpenContact = { opened = it }))
        }
        compose.onNodeWithText(string(R.string.cards_stats, 1, 1, 1)).assertExists()
        compose.onNodeWithText(string(R.string.cards_pending, 3)).performClick()
        compose.onNodeWithText("Ana Ruiz").performClick()
        assertTrue(queue)
        assertEquals("AnaRuiz", opened)
    }

    @Test
    fun activeFiltersAreChipsThatCanBeRemoved() {
        var applied: ContactFilter? = null
        compose.setContent {
            CardsContent(content(filter = ContactFilter(eventId = "e1", country = "Spain"), contacts = 5), CardsActions(), onApplyFilter = { applied = it })
        }
        compose.onNodeWithText(string(R.string.filters_count, 2)).assertExists()
        compose.onNodeWithContentDescription(string(R.string.filter_remove, string(R.string.filter_country_value, "Spain"))).performClick()
        assertEquals(ContactFilter(eventId = "e1"), applied)
    }

    @Test
    fun recentEventsFilterWithOneTap() {
        var applied: ContactFilter? = null
        compose.setContent { CardsContent(content(contacts = 2), CardsActions(), onApplyFilter = { applied = it }) }
        compose.onNodeWithText("SMM 2026").performClick()
        assertEquals("e1", applied?.eventId)
    }

    @Test
    fun theViewCanBeSwitched() {
        var view: CardsView? = null
        compose.setContent { CardsContent(content(items = listOf(item("Ana", "Ruiz"))), CardsActions(), onView = { view = it }) }
        compose.onNodeWithText(string(R.string.cards_view_list)).performClick()
        assertEquals(CardsView.LIST, view)
    }

    @Test
    fun loadingAndError() {
        compose.setContent { CardsContent(CardsUiState.Error, CardsActions()) }
        compose.onNodeWithText(string(R.string.cards_error)).assertExists()
    }

    @Test
    fun captureButtons() {
        var add = false
        var quick = false
        compose.setContent { CardsContent(CardsUiState.Loading, CardsActions(onAddCard = { add = true }, onQuickScan = { quick = true })) }
        compose.onNodeWithText(string(R.string.cards_add), useUnmergedTree = true).performClick()
        compose.onNodeWithText(string(R.string.cards_quick_scan), useUnmergedTree = true).performClick()
        assertTrue(add && quick)
    }
}

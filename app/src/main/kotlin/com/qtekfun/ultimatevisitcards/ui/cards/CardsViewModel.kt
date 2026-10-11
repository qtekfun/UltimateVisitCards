package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.ContactListItem
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.model.Stats
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.settings.CardsView
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A contact of the list with the image to show for it. */
data class CardItem(val item: ContactListItem, val image: File?)

/** What the filter sheet offers. */
data class FilterOptions(
    val events: List<EventSummary> = emptyList(),
    val sectors: List<Sector> = emptyList(),
    val companies: List<Company> = emptyList(),
    val countries: List<String> = emptyList()
)

sealed interface CardsUiState {
    data object Loading : CardsUiState

    data class Content(
        val items: List<CardItem>,
        val filter: ContactFilter,
        val stats: Stats,
        val pendingReview: Int,
        val view: CardsView,
        val options: FilterOptions
    ) : CardsUiState {
        /** Nothing at all yet (as opposed to a search with no results). */
        val noContacts: Boolean get() = stats.contacts == 0
    }

    data object Error : CardsUiState
}

/**
 * Home screen (docs/03 «Pantalla principal»): global search with combinable filters, stats, recent events,
 * pending-review banner and the contacts as a card grid or a compact list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactRepository,
    cards: BusinessCardRepository,
    companies: CompanyRepository,
    events: EventRepository,
    sectors: SectorRepository,
    private val store: ImageStore,
    private val preferences: UiPreferences
) : ViewModel() {
    /** The filter being edited; a global search opened from an event starts with its text (`q`). */
    private val filter = MutableStateFlow(ContactFilter(text = savedState.get<String>(ARG_QUERY).orEmpty()))

    private val items: Flow<List<CardItem>> = filter.flatMapLatest { contacts.observeList(it) }
        .map { list -> list.map { CardItem(it, image(it.primaryCardId)) } }

    private val stats: Flow<Stats> = combine(contacts.observeCount(), events.observeCount(), companies.observeCount()) { c, e, co -> Stats(c, e, co) }

    private val options: Flow<FilterOptions> =
        combine(events.observeAll(), sectors.observeAll(), companies.observeAll(), contacts.observeCountries()) { e, s, co, countries ->
            FilterOptions(e, s, co, countries)
        }

    val state: StateFlow<CardsUiState> = combine(items, filter, stats, cards.observeAwaitingReviewCount(), preferences.cardsView, options) { values ->
        @Suppress("UNCHECKED_CAST")
        CardsUiState.Content(
            items = values[0] as List<CardItem>,
            filter = values[1] as ContactFilter,
            stats = values[2] as Stats,
            pendingReview = values[3] as Int,
            view = values[4] as CardsView,
            options = values[5] as FilterOptions
        ) as CardsUiState
    }.catch { emit(CardsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), CardsUiState.Loading)

    private suspend fun image(cardId: String?): File? = cardId?.let {
        try {
            store.preview(it, CardSide.FRONT)
        } catch (e: IOException) {
            null
        }
    }

    fun search(text: String) = filter.update { it.copy(text = text) }

    fun applyFilter(newFilter: ContactFilter) = filter.update { newFilter.copy(text = it.text) }

    fun clearFilters() = filter.update { ContactFilter(text = it.text) }

    fun setView(view: CardsView) = preferences.setCardsView(view)

    companion object {
        /** Optional free text to start with (global search opened from an event). */
        const val ARG_QUERY = "q"
        private const val STOP_TIMEOUT = 5_000L
    }
}

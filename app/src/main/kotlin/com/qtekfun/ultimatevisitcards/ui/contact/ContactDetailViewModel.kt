package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A card of the contact with its stored images. */
data class CardImages(val card: BusinessCard, val front: File?, val back: File?)

sealed interface ContactDetailUiState {
    data object Loading : ContactDetailUiState

    data object Missing : ContactDetailUiState

    data class Content(val contact: Contact, val company: Company?, val sectorName: String?, val events: List<Event>, val cards: List<CardImages>) :
        ContactDetailUiState {
        /** The contact's country or, if missing, the company's (docs/02). */
        val country: String? get() = contact.country ?: company?.country
    }

    data object Error : ContactDetailUiState
}

/** Contact sheet (docs/03 «Ficha de contacto»); phase 6 shows the data and cards, phase 7 adds actions and editing. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    contacts: ContactRepository,
    companies: CompanyRepository,
    sectors: SectorRepository,
    cards: BusinessCardRepository,
    events: EventRepository,
    private val store: ImageStore
) : ViewModel() {
    val contactId: String = checkNotNull(savedState[ARG_CONTACT_ID])

    val state: StateFlow<ContactDetailUiState> = contacts.observe(contactId).flatMapLatest { contact ->
        if (contact == null) return@flatMapLatest flowOf(ContactDetailUiState.Missing)
        val company = contact.companyId?.let { companies.observe(it) } ?: flowOf(null)
        combine(company, cards.observeByContact(contactId), sectors.observeAll(), events.observeAll()) { co, cardList, sectorList, eventList ->
            val eventIds = contacts.eventIds(contactId)
            ContactDetailUiState.Content(
                contact = contact,
                company = co,
                sectorName = co?.sectorId?.let { id -> sectorList.firstOrNull { it.id == id }?.name },
                events = eventList.map { it.event }.filter { it.id in eventIds },
                cards = cardList.map { CardImages(it, image(it.frontProcessed), it.backProcessed?.let { path -> image(path) }) }
            ) as ContactDetailUiState
        }
    }.catch { emit(ContactDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ContactDetailUiState.Loading)

    private fun image(path: String): File? = try {
        store.file(path).takeIf { it.isFile }
    } catch (e: IOException) {
        null
    }

    companion object {
        const val ARG_CONTACT_ID = "contactId"
        private const val STOP_TIMEOUT = 5_000L
    }
}

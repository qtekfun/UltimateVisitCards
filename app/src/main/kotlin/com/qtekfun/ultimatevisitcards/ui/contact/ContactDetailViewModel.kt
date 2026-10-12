package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.contacts.PhoneContactResult
import com.qtekfun.ultimatevisitcards.data.contacts.PhoneContacts
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.export.VCardContact
import com.qtekfun.ultimatevisitcards.domain.export.VCardWriter
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeContactsUseCase
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeUndo
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import com.qtekfun.ultimatevisitcards.ui.merge.MergeUndoHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A card of the contact with its stored images. */
data class CardImages(val card: BusinessCard, val front: File?, val back: File?)

sealed interface ContactDetailUiState {
    data object Loading : ContactDetailUiState

    data object Missing : ContactDetailUiState

    data class Content(
        val contact: Contact,
        val company: Company?,
        val sectorName: String?,
        val events: List<Event>,
        val cards: List<CardImages>,
        /** Every event, to choose the contact's ones. */
        val allEvents: List<Event> = emptyList()
    ) : ContactDetailUiState {
        /** The contact's country or, if missing, the company's (docs/02). */
        val country: String? get() = contact.country ?: company?.country

        fun toVCard() = VCardContact(
            firstName = contact.firstName,
            lastName = contact.lastName,
            jobTitle = contact.jobTitle,
            companyName = company?.name,
            phone = contact.phone,
            mobile = contact.mobile,
            email = contact.email,
            website = contact.website,
            address = contact.address,
            country = country,
            notes = contact.notes
        )
    }

    data object Error : ContactDetailUiState
}

/** One-off outcomes the screen shows (snackbar) or acts on (share sheet). */
sealed interface ContactEvent {
    data class Share(val file: File) : ContactEvent

    data object AddedToPhone : ContactEvent

    data object PhoneFailed : ContactEvent

    data object PhonePermissionMissing : ContactEvent

    /** Already added on that date: ask before adding again (docs/05 §5). */
    data class AlreadyAddedToPhone(val at: Long) : ContactEvent

    data object CardDeleted : ContactEvent

    data object OnlyCard : ContactEvent

    data object MergeUndone : ContactEvent

    data object Failed : ContactEvent
}

/**
 * Contact sheet (docs/03 «Ficha de contacto»): data, quick actions, vCard sharing, «Add to phone contacts»,
 * card history (primary, delete, add), merge undo, delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    contacts: ContactRepository,
    companies: CompanyRepository,
    sectors: SectorRepository,
    cards: BusinessCardRepository,
    events: EventRepository,
    private val store: ImageStore,
    private val phone: PhoneContacts,
    private val merge: MergeContactsUseCase,
    private val undoHolder: MergeUndoHolder,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {
    val contactId: String = checkNotNull(savedState[ARG_CONTACT_ID])

    private val contactsRepo = contacts
    private val cardsRepo = cards

    private val _events = MutableStateFlow<ContactEvent?>(null)

    /** The pending one-off event; the screen calls [consumed] once it is shown. */
    val events: StateFlow<ContactEvent?> = _events.asStateFlow()

    /** A merge into this contact that can still be undone (this screen stays in the back stack during a merge). */
    private val _undo = MutableStateFlow<MergeUndo?>(null)
    val undo: StateFlow<MergeUndo?> = _undo.asStateFlow()

    /** The contact was deleted: the screen goes back. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

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
                cards = cardList.map { CardImages(it, image(it.frontProcessed), it.backProcessed?.let { path -> image(path) }) },
                allEvents = eventList.map { it.event }
            ) as ContactDetailUiState
        }
    }.catch { emit(ContactDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ContactDetailUiState.Loading)

    init {
        viewModelScope.launch {
            undoHolder.last.collect { pending -> if (pending != null) undoHolder.take(contactId)?.let { _undo.value = it } }
        }
    }

    /** The contact ends up in exactly the [chosen] events. */
    fun setEvents(chosen: Set<String>) {
        viewModelScope.launch {
            val current = contactsRepo.eventIds(contactId)
            val results = (chosen - current).map { contactsRepo.addToEvent(contactId, it) } +
                (current - chosen).map { contactsRepo.removeFromEvent(contactId, it) }
            if (results.any { it is DomainResult.Failure }) _events.value = ContactEvent.Failed
        }
    }

    fun consumed() {
        _events.value = null
    }

    fun setPrimary(cardId: String) {
        viewModelScope.launch { if (cardsRepo.setPrimary(cardId) is DomainResult.Failure) _events.value = ContactEvent.Failed }
    }

    /** Deletes an old card and its photos; the only card cannot go (docs/06 §5). */
    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            when (val result = cardsRepo.delete(cardId)) {
                is DomainResult.Success -> {
                    deleteFiles(listOf(cardId))
                    _events.value = ContactEvent.CardDeleted
                }
                is DomainResult.Failure -> _events.value = if (result.error is DomainError.NotAllowed) ContactEvent.OnlyCard else ContactEvent.Failed
            }
        }
    }

    /** Deletes the contact with all its cards and their photos. */
    fun deleteContact() {
        viewModelScope.launch {
            val cardIds = cardsRepo.observeByContact(contactId).first().map { it.id }
            if (contactsRepo.delete(contactId) is DomainResult.Success) {
                deleteFiles(cardIds)
                _deleted.value = true
            } else {
                _events.value = ContactEvent.Failed
            }
        }
    }

    private suspend fun deleteFiles(cardIds: List<String>) {
        cardIds.forEach {
            try {
                store.deleteCard(it)
            } catch (e: IOException) {
                // Leftovers are removed by ImageStore.deleteOrphans.
            }
        }
    }

    /** Writes the contact as a .vcf in the app cache, for the share sheet (via FileProvider). */
    fun shareVCard() {
        val content = state.value as? ContactDetailUiState.Content ?: return
        viewModelScope.launch {
            try {
                val file = withContext(io) {
                    val dir = store.sharedDir().apply { mkdirs() }
                    File(dir, fileName(content.contact) + ".vcf").apply { writeText(VCardWriter.toString(content.toVCard()), Charsets.UTF_8) }
                }
                _events.value = ContactEvent.Share(file)
            } catch (e: IOException) {
                _events.value = ContactEvent.Failed
            }
        }
    }

    /** «Add to phone contacts»: asks first if it was already added (no READ_CONTACTS to check the phone). */
    fun addToPhone(again: Boolean = false) {
        val content = state.value as? ContactDetailUiState.Content ?: return
        val previous = content.contact.exportedToPhoneAt
        if (previous != null && !again) {
            _events.value = ContactEvent.AlreadyAddedToPhone(previous)
            return
        }
        viewModelScope.launch {
            val result = withContext(io) { phone.add(content.toVCard()) }
            _events.value = when (result) {
                is PhoneContactResult.Added -> {
                    contactsRepo.markExportedToPhone(contactId)
                    ContactEvent.AddedToPhone
                }
                is PhoneContactResult.Failed ->
                    if (result.reason == PhoneContactResult.Reason.PERMISSION) ContactEvent.PhonePermissionMissing else ContactEvent.PhoneFailed
            }
        }
    }

    fun undoMerge() {
        val pending = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch {
            _events.value = if (merge.undo(pending) is DomainResult.Success) ContactEvent.MergeUndone else ContactEvent.Failed
        }
    }

    fun dismissUndo() {
        _undo.value = null
    }

    private fun image(path: String): File? = try {
        store.file(path).takeIf { it.isFile }
    } catch (e: IOException) {
        null
    }

    private fun fileName(contact: Contact): String =
        "${contact.firstName} ${contact.lastName}".trim().ifEmpty { "contact" }.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")

    companion object {
        const val ARG_CONTACT_ID = "contactId"
        private const val STOP_TIMEOUT = 5_000L
    }
}

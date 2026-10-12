package com.qtekfun.ultimatevisitcards.ui.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.ContactListItem
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventDetailUiState(
    val loading: Boolean = true,
    /** The event does not exist (deleted elsewhere). */
    val missing: Boolean = false,
    val error: Boolean = false,
    val event: Event? = null,
    /** Text of the search limited to this event. */
    val query: String = "",
    val contacts: List<ContactListItem> = emptyList(),
    /** The edit dialog, when open. */
    val editor: EditorState? = null,
    /** The event was deleted: the screen goes back. */
    val deleted: Boolean = false,
    /** «Contacts in this event» picker, when open: every contact and which ones are in the event. */
    val picker: ContactPicker? = null,
    /** Saving the picker's choice failed. */
    val membershipFailed: Boolean = false
)

data class ContactPicker(val contacts: List<ContactListItem>, val inEvent: Set<String>)

/**
 * One event (docs/03): its data, edit and delete, and its contacts with a search limited to the event. The
 * «Only in this event» chip, when removed, turns it into the global search (handled by the screen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val events: EventRepository, private val contacts: ContactRepository) :
    ViewModel() {
    val eventId: String = checkNotNull(savedState[ARG_EVENT_ID])

    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val results = query.flatMapLatest { text -> contacts.observeList(ContactFilter(text = text, eventId = eventId)) }
            combine(events.observe(eventId), results) { event, list -> event to list }
                .catch { _state.update { it.copy(loading = false, error = true) } }
                .collect { (event, list) ->
                    _state.update { it.copy(loading = false, missing = event == null && !it.deleted, event = event, contacts = list) }
                }
        }
    }

    fun onQuery(text: String) {
        query.value = text
        _state.update { it.copy(query = text) }
    }

    fun startEdit() = _state.update { it.copy(editor = EditorState()) }

    fun cancelEdit() = _state.update { it.copy(editor = null) }

    fun save(draft: EventDraft) {
        val current = _state.value.event ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = draft.name,
                location = draft.location,
                startDate = draft.startDate,
                endDate = draft.endDate,
                notes = draft.notes
            )
            when (val result = events.update(updated)) {
                is DomainResult.Success -> _state.update { it.copy(editor = null) }
                is DomainResult.Failure -> _state.update { it.copy(editor = EditorState(result.error.toEditError())) }
            }
        }
    }

    /** Opens the picker with every contact, the ones already in the event checked. */
    fun startPicking() {
        viewModelScope.launch {
            val all = contacts.observeList(ContactFilter()).first()
            val inEvent = contacts.observeList(ContactFilter(eventId = eventId)).first().map { it.contact.id }.toSet()
            _state.update { it.copy(picker = ContactPicker(all, inEvent), membershipFailed = false) }
        }
    }

    fun cancelPicking() = _state.update { it.copy(picker = null) }

    /** The event ends up with exactly [chosen] among the picker's contacts: new ones are added, unchecked ones removed. */
    fun saveMembers(chosen: Set<String>) {
        val picker = _state.value.picker ?: return
        viewModelScope.launch {
            val results = (chosen - picker.inEvent).map { contacts.addToEvent(it, eventId) } +
                (picker.inEvent - chosen).map { contacts.removeFromEvent(it, eventId) }
            val failed = results.any { it is DomainResult.Failure }
            _state.update { it.copy(picker = null, membershipFailed = failed) }
        }
    }

    fun membershipErrorShown() = _state.update { it.copy(membershipFailed = false) }

    /** Deletes the event; its contacts and cards stay. */
    fun delete() {
        viewModelScope.launch {
            if (events.delete(eventId) is DomainResult.Success) _state.update { it.copy(deleted = true) }
        }
    }

    companion object {
        const val ARG_EVENT_ID = "eventId"
    }
}

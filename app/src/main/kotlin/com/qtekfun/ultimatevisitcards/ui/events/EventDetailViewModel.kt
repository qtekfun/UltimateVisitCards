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
    val deleted: Boolean = false
)

/**
 * One event (docs/03): its data, edit and delete, and its contacts with a search limited to the event. The
 * «Only in this event» chip, when removed, turns it into the global search (handled by the screen).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val events: EventRepository, contacts: ContactRepository) : ViewModel() {
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

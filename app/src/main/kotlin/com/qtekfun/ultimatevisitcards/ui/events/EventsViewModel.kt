package com.qtekfun.ultimatevisitcards.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why an edit dialog could not be saved. */
enum class EditError { NAME_REQUIRED, NAME_TAKEN, SAVE_FAILED }

/** An open create/edit dialog and its last error. */
data class EditorState(val error: EditError? = null)

/** Maps a failed save to what the dialog shows. */
internal fun DomainError.toEditError(): EditError = when (this) {
    is DomainError.Invalid -> EditError.NAME_REQUIRED
    is DomainError.Conflict -> EditError.NAME_TAKEN
    else -> EditError.SAVE_FAILED
}

/** Events list (docs/03, «Eventos»): name, dates, place and «N contacts», with «New event». */
@HiltViewModel
class EventsViewModel @Inject constructor(private val events: EventRepository) : ViewModel() {
    val state: StateFlow<LoadState<EventSummary>> = events.observeAll()
        .map<List<EventSummary>, LoadState<EventSummary>> { LoadState.Data(it) }
        .catch { emit(LoadState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LoadState.Loading)

    private val _editor = MutableStateFlow<EditorState?>(null)

    /** The «New event» dialog, when open. */
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    fun startCreate() {
        _editor.value = EditorState()
    }

    fun cancelCreate() {
        _editor.value = null
    }

    fun create(draft: EventDraft) {
        viewModelScope.launch {
            when (val result = events.create(draft)) {
                is DomainResult.Success -> _editor.value = null
                is DomainResult.Failure -> _editor.value = EditorState(result.error.toEditError())
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

package com.qtekfun.ultimatevisitcards.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicatePair
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateScanner
import com.qtekfun.ultimatevisitcards.domain.dedupe.MatchReason
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import com.qtekfun.ultimatevisitcards.domain.util.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A possible duplicate: merging keeps [keep] (the older contact) and folds [other] into it. */
data class DuplicateRow(val keep: ContactWithCompany, val other: ContactWithCompany, val reason: MatchReason)

sealed interface DuplicatesUiState {
    data object Loading : DuplicatesUiState

    data object Empty : DuplicatesUiState

    data object Error : DuplicatesUiState

    data class Content(val rows: List<DuplicateRow>) : DuplicatesUiState
}

/** «Posibles duplicados» in Settings (docs/06 §4): scans every contact, best matches first. */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val contacts: ContactRepository,
    private val scanner: DuplicateScanner,
    @DefaultDispatcher private val default: CoroutineDispatcher
) : ViewModel() {
    private val _state = MutableStateFlow<DuplicatesUiState>(DuplicatesUiState.Loading)
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Scans again, e.g. when coming back from a merge. Keeps the current list on screen meanwhile. */
    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = try {
                val all = contacts.allWithCompany()
                val rows = withContext(default) { scanner.scan(all).map { it.toRow() } }
                if (rows.isEmpty()) DuplicatesUiState.Empty else DuplicatesUiState.Content(rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DuplicatesUiState.Error
            }
        }
    }

    private fun DuplicatePair.toRow(): DuplicateRow {
        val aFirst = a.contact.createdAt < b.contact.createdAt || (a.contact.createdAt == b.contact.createdAt && a.contact.id <= b.contact.id)
        return if (aFirst) DuplicateRow(a, b, reason) else DuplicateRow(b, a, reason)
    }
}

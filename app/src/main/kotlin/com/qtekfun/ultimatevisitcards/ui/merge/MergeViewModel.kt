package com.qtekfun.ultimatevisitcards.ui.merge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSide
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.ContactListItem
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MergeUiState(
    val keep: Contact? = null,
    /** Step 1: pick the other contact. */
    val query: String = "",
    val candidates: List<ContactListItem> = emptyList(),
    /** Step 2: the comparison with the picked one. */
    val other: Contact? = null,
    val plan: MergePlan? = null,
    val merging: Boolean = false,
    val failed: Boolean = false,
    /** Merged: the screen closes and the contact sheet offers «Undo». */
    val done: Boolean = false
)

/** Manual merge (docs/06 §4): pick another contact, compare field by field, merge into this one. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MergeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactRepository,
    private val companies: CompanyRepository,
    private val cards: BusinessCardRepository,
    private val merge: MergeContactsUseCase,
    private val undoHolder: MergeUndoHolder
) : ViewModel() {
    val keepId: String = checkNotNull(savedState[ARG_KEEP_ID])

    /** Set when coming from «Possible duplicates»: the comparison opens straight away. */
    private val preselectedId: String? = savedState.get<String>(ARG_OTHER_ID)?.takeIf { it.isNotEmpty() }

    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(keep = contacts.get(keepId)) } }
        viewModelScope.launch {
            query.flatMapLatest { contacts.observeList(ContactFilter(text = it)) }.collect { list ->
                _state.update { it.copy(candidates = list.filter { item -> item.contact.id != keepId }) }
            }
        }
        preselectedId?.let { id -> viewModelScope.launch { contacts.get(id)?.let(::pick) } }
    }

    fun onQuery(text: String) {
        query.value = text
        _state.update { it.copy(query = text) }
    }

    fun pick(other: Contact) {
        viewModelScope.launch {
            val keep = contacts.get(keepId) ?: return@launch
            _state.update { it.copy(other = other, plan = MergePlan.of(side(keep), side(other))) }
        }
    }

    fun back() = _state.update { it.copy(other = null, plan = null) }

    fun choose(field: MergeField, source: MergeSource) = _state.update { it.copy(plan = it.plan?.choose(field, source)) }

    fun confirm() {
        val s = _state.value
        val other = s.other ?: return
        val plan = s.plan ?: return
        _state.update { it.copy(merging = true, failed = false) }
        viewModelScope.launch {
            when (val result = merge(keepId, other.id, plan)) {
                is DomainResult.Success -> {
                    undoHolder.offer(result.value)
                    _state.update { it.copy(merging = false, done = true) }
                }
                is DomainResult.Failure -> _state.update { it.copy(merging = false, failed = true) }
            }
        }
    }

    private suspend fun side(contact: Contact) = MergeSide.of(
        contact,
        contact.companyId?.let { companies.get(it)?.name },
        cards.observeByContact(contact.id).first().maxOfOrNull { it.capturedAt } ?: contact.createdAt
    )

    companion object {
        const val ARG_KEEP_ID = "keepId"
        const val ARG_OTHER_ID = "otherId"
    }
}

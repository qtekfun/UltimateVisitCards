package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.ui.events.EditorState
import com.qtekfun.ultimatevisitcards.ui.events.toEditError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the company edit dialog saves. */
data class CompanyEdit(val name: String, val sectorId: String?, val country: String, val website: String, val notes: String)

data class CompanyDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val error: Boolean = false,
    val company: Company? = null,
    val sectorName: String? = null,
    val sectors: List<Sector> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val editor: EditorState? = null,
    val deleted: Boolean = false,
)

/** One company (docs/03): sector, country, web, notes and its contacts; edit and delete. */
@HiltViewModel
class CompanyDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val companies: CompanyRepository,
    contacts: ContactRepository,
    sectors: SectorRepository,
) : ViewModel() {
    val companyId: String = checkNotNull(savedState[ARG_COMPANY_ID])

    private val _state = MutableStateFlow(CompanyDetailUiState())
    val state: StateFlow<CompanyDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(companies.observe(companyId), contacts.observeByCompany(companyId), sectors.observeAll()) { company, people, sectorList ->
                Triple(company, people, sectorList)
            }.catch { _state.update { it.copy(loading = false, error = true) } }
                .collect { (company, people, sectorList) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            missing = company == null && !it.deleted,
                            company = company,
                            sectorName = sectorList.firstOrNull { s -> s.id == company?.sectorId }?.name,
                            sectors = sectorList,
                            contacts = people,
                        )
                    }
                }
        }
    }

    fun startEdit() = _state.update { it.copy(editor = EditorState()) }

    fun cancelEdit() = _state.update { it.copy(editor = null) }

    /** Saves the edit; another company with the same normalised name is reported as a conflict. */
    fun save(edit: CompanyEdit) {
        val current = _state.value.company ?: return
        viewModelScope.launch {
            val updated = current.copy(name = edit.name, sectorId = edit.sectorId, country = edit.country, website = edit.website, notes = edit.notes)
            when (val result = companies.update(updated)) {
                is DomainResult.Success -> _state.update { it.copy(editor = null) }
                is DomainResult.Failure -> _state.update { it.copy(editor = EditorState(result.error.toEditError())) }
            }
        }
    }

    /** Deletes the company; its contacts stay, without a company. */
    fun delete() {
        viewModelScope.launch {
            if (companies.delete(companyId) is DomainResult.Success) _state.update { it.copy(deleted = true) }
        }
    }

    companion object {
        const val ARG_COMPANY_ID = "companyId"
    }
}

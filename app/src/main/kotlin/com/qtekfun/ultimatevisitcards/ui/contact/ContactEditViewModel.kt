package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.domain.usecase.UpdateContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields of the edit form, in display order. */
val EDIT_FIELDS = listOf(
    CardField.FIRST_NAME,
    CardField.LAST_NAME,
    CardField.JOB_TITLE,
    CardField.COMPANY,
    CardField.COUNTRY,
    CardField.PHONE,
    CardField.MOBILE,
    CardField.EMAIL,
    CardField.WEBSITE,
    CardField.ADDRESS,
    CardField.NOTES,
)

enum class EditError { EMPTY, SAVE_FAILED }

data class ContactEditUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val values: Map<CardField, String> = emptyMap(),
    val companySuggestions: List<Company> = emptyList(),
    val saving: Boolean = false,
    val error: EditError? = null,
    val saved: Boolean = false,
) {
    fun value(field: CardField) = values[field].orEmpty()
}

/** Editing a contact (docs/07 fase 7, «Ficha completa y edición»). */
@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactRepository,
    private val companies: CompanyRepository,
    private val update: UpdateContactUseCase,
) : ViewModel() {
    val contactId: String = checkNotNull(savedState[ContactDetailViewModel.ARG_CONTACT_ID])

    private val _state = MutableStateFlow(ContactEditUiState())
    val state: StateFlow<ContactEditUiState> = _state.asStateFlow()

    private var original: Contact? = null
    private var knownCompanies: List<Company> = emptyList()

    init {
        viewModelScope.launch {
            val contact = contacts.get(contactId)
            if (contact == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            original = contact
            knownCompanies = companies.observeAll().first()
            val companyName = contact.companyId?.let { id -> knownCompanies.firstOrNull { it.id == id }?.name }
            _state.update {
                it.copy(
                    loading = false,
                    values = mapOf(
                        CardField.FIRST_NAME to contact.firstName,
                        CardField.LAST_NAME to contact.lastName,
                        CardField.JOB_TITLE to contact.jobTitle.orEmpty(),
                        CardField.COMPANY to companyName.orEmpty(),
                        CardField.COUNTRY to contact.country.orEmpty(),
                        CardField.PHONE to contact.phone.orEmpty(),
                        CardField.MOBILE to contact.mobile.orEmpty(),
                        CardField.EMAIL to contact.email.orEmpty(),
                        CardField.WEBSITE to contact.website.orEmpty(),
                        CardField.ADDRESS to contact.address.orEmpty(),
                        CardField.NOTES to contact.notes.orEmpty(),
                    ),
                )
            }
        }
    }

    fun onChange(field: CardField, value: String) = _state.update { current ->
        val suggestions = if (field == CardField.COMPANY) {
            val key = TextNormalizer.fold(value)
            if (key.length < 2) emptyList() else knownCompanies.filter { TextNormalizer.fold(it.name).contains(key) && TextNormalizer.fold(it.name) != key }.take(MAX_SUGGESTIONS)
        } else {
            current.companySuggestions
        }
        current.copy(values = current.values + (field to value), companySuggestions = suggestions, error = null)
    }

    fun chooseCompany(company: Company) = _state.update { it.copy(values = it.values + (CardField.COMPANY to company.name), companySuggestions = emptyList()) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun save() {
        val contact = original ?: return
        val s = _state.value
        if (s.saving) return
        fun v(field: CardField) = s.value(field).trim().takeIf { it.isNotEmpty() }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val edited = contact.copy(
                firstName = v(CardField.FIRST_NAME).orEmpty(),
                lastName = v(CardField.LAST_NAME).orEmpty(),
                jobTitle = v(CardField.JOB_TITLE),
                country = v(CardField.COUNTRY),
                phone = v(CardField.PHONE),
                mobile = v(CardField.MOBILE),
                email = v(CardField.EMAIL),
                website = v(CardField.WEBSITE),
                address = v(CardField.ADDRESS),
                notes = v(CardField.NOTES),
            )
            when (val result = update(edited, v(CardField.COMPANY))) {
                is DomainResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                is DomainResult.Failure -> _state.update {
                    it.copy(saving = false, error = if (result.error is DomainError.Invalid) EditError.EMPTY else EditError.SAVE_FAILED)
                }
            }
        }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 5
    }
}

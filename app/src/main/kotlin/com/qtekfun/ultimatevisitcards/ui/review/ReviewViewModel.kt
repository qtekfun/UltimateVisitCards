package com.qtekfun.ultimatevisitcards.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.dedupe.ContactProbe
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateCandidate
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateDetector
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractionDictionaries
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSide
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.domain.usecase.AddCardToContactUseCase
import com.qtekfun.ultimatevisitcards.domain.usecase.ReviewedCard
import com.qtekfun.ultimatevisitcards.domain.usecase.SaveReviewedCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where to go after saving. */
sealed interface ReviewExit {
    /** Back to where the user came from. */
    data object Done : ReviewExit

    /** «Save and scan next»: straight to the camera. */
    data object ScanNext : ReviewExit

    /** «Save and review next» (from the queue): the next pending card. */
    data class ReviewNext(val cardId: String) : ReviewExit
}

/** What the secondary save button does. */
enum class SaveAndNext { SCAN, REVIEW }

enum class ReviewError { EMPTY, SAVE_FAILED }

/** The fields of the form, in display order (docs/03, «Revisión»). */
val REVIEW_FIELDS = listOf(
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
    CardField.NOTES
)

data class ReviewUiState(
    val loading: Boolean = true,
    /** The card does not exist (deleted elsewhere). */
    val missing: Boolean = false,
    /** OCR still running: the form can be filled meanwhile; untouched fields are filled when it ends. */
    val reading: Boolean = false,
    /** OCR failed: empty form, photo visible, nothing lost. */
    val ocrFailed: Boolean = false,
    val values: Map<CardField, String> = emptyMap(),
    /** Fields the OCR proposed with low confidence: highlighted. */
    val doubtful: Set<CardField> = emptySet(),
    val side: CardSide = CardSide.FRONT,
    val frontImage: File? = null,
    val backImage: File? = null,
    val events: List<EventSummary> = emptyList(),
    val sectors: List<Sector> = emptyList(),
    val eventId: String? = null,
    val sectorId: String? = null,
    val companySuggestions: List<Company> = emptyList(),
    val countrySuggestions: List<String> = emptyList(),
    /** Possible duplicates found on save: the user decides before anything is written. */
    val duplicates: List<DuplicateCandidate> = emptyList(),
    /** An existing contact opened from the duplicate warning. */
    val preview: DuplicateCandidate? = null,
    /** «Merge» with an existing contact: the field-by-field comparison (existing ← this card). */
    val merge: MergeTarget? = null,
    val saving: Boolean = false,
    val error: ReviewError? = null,
    val exit: ReviewExit? = null
) {
    fun value(field: CardField): String = values[field].orEmpty()
}

/** The existing contact this card is being merged into, and the plan of chosen values. */
data class MergeTarget(val contact: Contact, val plan: MergePlan)

/**
 * Review of one card (docs/03, «Revisión»): the photo, the fields proposed by the OCR (doubtful ones
 * highlighted), company and country suggestions, event and sector with quick creation, the duplicate
 * warning, and the save.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val cards: BusinessCardRepository,
    private val companies: CompanyRepository,
    private val contacts: ContactRepository,
    private val events: EventRepository,
    private val sectors: SectorRepository,
    private val store: ImageStore,
    private val detector: DuplicateDetector,
    private val saveCard: SaveReviewedCardUseCase,
    private val addCard: AddCardToContactUseCase
) : ViewModel() {
    val cardId: String = checkNotNull(savedState[ARG_CARD_ID])

    /** A new card for this existing contact («Add new card» in the contact sheet): saving compares and merges. */
    private val targetContactId: String? = savedState.get<String>(ARG_TARGET_CONTACT)?.takeIf { it.isNotEmpty() }

    /** Opened from the review queue: «Save and review next» instead of «Save and scan next». */
    val next: SaveAndNext = if (savedState.get<Boolean>(ARG_FROM_QUEUE) == true) SaveAndNext.REVIEW else SaveAndNext.SCAN

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private val touched = mutableSetOf<CardField>()
    private var filledFromOcr = false
    private var knownCompanies: List<Company> = emptyList()
    private var duplicatesConfirmed = false
    private var pendingExit: ReviewExit = ReviewExit.Done
    private val companyKeys = CompanyNameNormalizer.fromBundledDictionary()
    private val countries = ExtractionDictionaries.bundled.countries

    init {
        viewModelScope.launch {
            combine(cards.observe(cardId), events.observeAll(), sectors.observeAll(), companies.observeAll()) { card, events, sectors, companies ->
                knownCompanies = companies
                Snapshot(card, events, sectors)
            }.catch { _state.update { it.copy(loading = false, missing = true) } }
                .collect { onSnapshot(it) }
        }
    }

    private class Snapshot(val card: BusinessCard?, val events: List<EventSummary>, val sectors: List<Sector>)

    private fun onSnapshot(snapshot: Snapshot) {
        val card = snapshot.card
        if (card == null) {
            _state.update { it.copy(loading = false, missing = true) }
            return
        }
        val reading = card.ocrStatus == OcrStatus.PENDING || card.ocrStatus == OcrStatus.PROCESSING
        _state.update { current ->
            var next = current.copy(
                loading = false,
                reading = reading,
                ocrFailed = card.ocrStatus == OcrStatus.OCR_ERROR,
                frontImage = image(card.frontProcessed),
                backImage = card.backProcessed?.let { image(it) },
                events = snapshot.events,
                sectors = snapshot.sectors,
                eventId = current.eventId ?: card.eventId
            )
            if (!reading && !filledFromOcr) {
                filledFromOcr = true
                next = fill(next, ExtractedFields.fromJson(card.extractedJson) ?: ExtractedFields())
            }
            next
        }
    }

    private fun image(path: String): File? = try {
        store.file(path).takeIf { it.isFile }
    } catch (e: IOException) {
        null
    }

    /** OCR proposals go into the fields the user has not typed in yet. */
    private fun fill(state: ReviewUiState, fields: ExtractedFields): ReviewUiState {
        val values = state.values.toMutableMap()
        REVIEW_FIELDS.filter { it !in touched }.forEach { field -> fields.value(field)?.let { values[field] = it } }
        val doubtful = fields.doubtful(ExtractedFields.DEFAULT_THRESHOLD).filter { it !in touched }.toSet()
        val sector = state.sectorId ?: matchingCompany(values[CardField.COMPANY])?.sectorId
        return state.copy(values = values, doubtful = doubtful, sectorId = sector)
    }

    private fun matchingCompany(name: String?): Company? {
        val key = name?.let { companyKeys.normalize(it) }?.takeIf { it.isNotEmpty() } ?: return null
        return knownCompanies.firstOrNull { companyKeys.normalize(it.name) == key }
    }

    fun onChange(field: CardField, value: String) {
        touched += field
        duplicatesConfirmed = false
        _state.update { current ->
            var next = current.copy(values = current.values + (field to value), doubtful = current.doubtful - field, error = null)
            when (field) {
                CardField.COMPANY -> {
                    next = next.copy(companySuggestions = companySuggestions(value))
                    // Typing an existing company brings its sector.
                    matchingCompany(value)?.sectorId?.let { next = next.copy(sectorId = it) }
                }
                CardField.COUNTRY -> next = next.copy(countrySuggestions = countrySuggestions(value))
                else -> Unit
            }
            next
        }
    }

    private fun companySuggestions(typed: String): List<Company> {
        val key = TextNormalizer.fold(typed)
        if (key.length < 2) return emptyList()
        return knownCompanies.filter { TextNormalizer.fold(it.name).contains(key) && TextNormalizer.fold(it.name) != key }.take(MAX_SUGGESTIONS)
    }

    private fun countrySuggestions(typed: String): List<String> {
        val tokens = TextNormalizer.tokens(typed)
        if (tokens.isEmpty() || TextNormalizer.fold(typed).length < 2) return emptyList()
        val prefix = tokens.joinToString(" ")
        return countries.filter { c -> c.aliases.any { it.joinToString(" ").startsWith(prefix) } }
            .map { it.name }
            .filter { it != typed }
            .take(MAX_SUGGESTIONS)
    }

    fun chooseCompany(company: Company) {
        touched += CardField.COMPANY
        _state.update {
            it.copy(
                values = it.values + (CardField.COMPANY to company.name),
                companySuggestions = emptyList(),
                sectorId = company.sectorId ?: it.sectorId,
                doubtful = it.doubtful - CardField.COMPANY
            )
        }
    }

    fun chooseCountry(country: String) {
        touched += CardField.COUNTRY
        _state.update {
            it.copy(
                values = it.values + (CardField.COUNTRY to country),
                countrySuggestions = emptyList(),
                doubtful =
                    it.doubtful - CardField.COUNTRY
            )
        }
    }

    fun showSide(side: CardSide) = _state.update { it.copy(side = side) }

    fun chooseEvent(eventId: String?) = _state.update { it.copy(eventId = eventId) }

    fun chooseSector(sectorId: String?) = _state.update { it.copy(sectorId = sectorId) }

    /** «Create new» without leaving the screen; the new event is selected. */
    fun createEvent(name: String) {
        viewModelScope.launch {
            when (val result = events.create(EventDraft(name))) {
                is DomainResult.Success -> _state.update { it.copy(eventId = result.value.id) }
                is DomainResult.Failure -> Unit // Blank name: the dialog does not allow it.
            }
        }
    }

    /** «Create new» sector; an existing one with the same name is selected instead. */
    fun createSector(name: String) {
        viewModelScope.launch {
            when (val result = sectors.create(name)) {
                is DomainResult.Success -> _state.update { it.copy(sectorId = result.value.id) }
                is DomainResult.Failure -> (result.error as? DomainError.Conflict)?.let { conflict ->
                    _state.update { it.copy(sectorId = conflict.existingId) }
                }
            }
        }
    }

    /** «Save»: back to where the user came from. */
    fun save() = save(ReviewExit.Done)

    /** «Save and scan next» or «Save and review next», depending on where the review was opened from. */
    fun saveAndNext() = save(if (next == SaveAndNext.SCAN) ReviewExit.ScanNext else NEXT_IN_QUEUE)

    /** Checks for duplicates first; when there are, the warning is shown and nothing is saved yet. */
    private fun save(exit: ReviewExit) {
        val current = _state.value
        if (current.saving) return
        val input = reviewed(current)
        if (input.isEmpty) {
            _state.update { it.copy(error = ReviewError.EMPTY) }
            return
        }
        targetContactId?.let { target ->
            pendingExit = exit
            viewModelScope.launch { contacts.get(target)?.let { openMerge(it) } }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            if (!duplicatesConfirmed) {
                val probe = ContactProbe(input.firstName, input.lastName, input.companyName, input.email, input.phone, input.mobile)
                val found = detector.find(probe, contacts.allWithCompany())
                if (found.isNotEmpty()) {
                    pendingExit = exit
                    _state.update { it.copy(saving = false, duplicates = found) }
                    return@launch
                }
            }
            when (val result = saveCard(input)) {
                is DomainResult.Success -> _state.update { it.copy(saving = false, exit = resolve(exit)) }
                is DomainResult.Failure -> _state.update {
                    it.copy(saving = false, error = if (result.error is DomainError.Invalid) ReviewError.EMPTY else ReviewError.SAVE_FAILED)
                }
            }
        }
    }

    /** «Save as new» from the duplicate warning. */
    fun saveAsNew() {
        duplicatesConfirmed = true
        _state.update { it.copy(duplicates = emptyList(), preview = null) }
        save(pendingExit)
    }

    /** «Merge» from the warning: compare field by field with [candidate], newest card wins by default. */
    fun mergeInto(candidate: DuplicateCandidate) {
        viewModelScope.launch { openMerge(candidate.contact) }
    }

    /** «Add as new card»: the card joins [candidate]'s history; its data only fills empty fields. */
    fun addAsNewCard(candidate: DuplicateCandidate) {
        viewModelScope.launch {
            val plan = plan(candidate.contact)
            val keepExisting = plan.conflicts.fold(plan) { p, field -> p.choose(field, MergeSource.EXISTING) }
            finishMerge(candidate.contact, keepExisting)
        }
    }

    fun chooseMerge(field: MergeField, source: MergeSource) = _state.update { it.copy(merge = it.merge?.copy(plan = it.merge.plan.choose(field, source))) }

    fun cancelMerge() = _state.update { it.copy(merge = null) }

    fun confirmMerge() {
        val target = _state.value.merge ?: return
        viewModelScope.launch { finishMerge(target.contact, target.plan) }
    }

    private suspend fun openMerge(contact: Contact) {
        _state.update { it.copy(duplicates = emptyList(), preview = null, merge = MergeTarget(contact, plan(contact))) }
    }

    private suspend fun plan(contact: Contact): MergePlan {
        val existing = MergeSide.of(
            contact,
            contact.companyId?.let { companies.get(it)?.name },
            cards.observeByContact(contact.id).first().maxOfOrNull { it.capturedAt } ?: contact.createdAt
        )
        val s = _state.value
        fun v(field: CardField) = s.value(field).trim().takeIf { it.isNotEmpty() }
        val incoming = MergeSide(
            mapOf(
                MergeField.FIRST_NAME to v(CardField.FIRST_NAME),
                MergeField.LAST_NAME to v(CardField.LAST_NAME),
                MergeField.JOB_TITLE to v(CardField.JOB_TITLE),
                MergeField.COMPANY to v(CardField.COMPANY),
                MergeField.COUNTRY to v(CardField.COUNTRY),
                MergeField.PHONE to v(CardField.PHONE),
                MergeField.MOBILE to v(CardField.MOBILE),
                MergeField.EMAIL to v(CardField.EMAIL),
                MergeField.WEBSITE to v(CardField.WEBSITE),
                MergeField.ADDRESS to v(CardField.ADDRESS)
            ),
            notes = v(CardField.NOTES),
            newestCardAt = cards.get(cardId)?.capturedAt ?: Long.MAX_VALUE
        )
        return MergePlan.of(existing, incoming)
    }

    private suspend fun finishMerge(contact: Contact, plan: MergePlan) {
        _state.update { it.copy(saving = true) }
        when (addCard(cardId, contact.id, plan, _state.value.eventId, _state.value.sectorId)) {
            is DomainResult.Success -> _state.update { it.copy(saving = false, merge = null, duplicates = emptyList(), exit = resolve(pendingExit)) }
            is DomainResult.Failure -> _state.update { it.copy(saving = false, error = ReviewError.SAVE_FAILED) }
        }
    }

    /** «View existing contact»: opens it without losing the review. */
    fun viewExisting(candidate: DuplicateCandidate) = _state.update { it.copy(preview = candidate) }

    fun closePreview() = _state.update { it.copy(preview = null) }

    fun dismissDuplicates() = _state.update { it.copy(duplicates = emptyList(), preview = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** The next card of the queue (oldest first, not this one), or done when the queue is empty. */
    private suspend fun resolve(exit: ReviewExit): ReviewExit {
        if (exit != NEXT_IN_QUEUE) return exit
        val next = cards.observeAwaitingReview().first().firstOrNull { it.id != cardId }
        return next?.let { ReviewExit.ReviewNext(it.id) } ?: ReviewExit.Done
    }

    private fun reviewed(state: ReviewUiState): ReviewedCard {
        fun v(field: CardField) = state.value(field).trim().takeIf { it.isNotEmpty() }
        return ReviewedCard(
            cardId = cardId,
            firstName = v(CardField.FIRST_NAME).orEmpty(),
            lastName = v(CardField.LAST_NAME).orEmpty(),
            jobTitle = v(CardField.JOB_TITLE),
            companyName = v(CardField.COMPANY),
            sectorId = state.sectorId,
            country = v(CardField.COUNTRY),
            phone = v(CardField.PHONE),
            mobile = v(CardField.MOBILE),
            email = v(CardField.EMAIL),
            website = v(CardField.WEBSITE),
            address = v(CardField.ADDRESS),
            notes = v(CardField.NOTES),
            eventId = state.eventId
        )
    }

    companion object {
        const val ARG_CARD_ID = "cardId"
        const val ARG_FROM_QUEUE = "queue"
        const val ARG_TARGET_CONTACT = "contactId"

        /** Placeholder resolved after saving into [ReviewExit.ReviewNext] or [ReviewExit.Done]. */
        private val NEXT_IN_QUEUE = ReviewExit.ReviewNext("")
        private const val MAX_SUGGESTIONS = 5
    }
}

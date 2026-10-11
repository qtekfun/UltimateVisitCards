package com.qtekfun.ultimatevisitcards.domain.usecase

import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.TransactionRunner
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Everything needed to undo a merge for a few seconds after it (docs/06 §4, «Deshacer»). */
data class MergeUndo(
    val keptBefore: Contact,
    val keptEventIds: Set<String>,
    val discarded: Contact,
    val discardedEventIds: Set<String>,
    val discardedCardIds: List<String>
)

/**
 * Merges two contacts into [keep] with the values chosen in [plan] (docs/06 §4), in one transaction:
 * chosen fields (the company is linked by name or created), notes joined, events united, every card moved
 * (one primary: the newest), the other contact deleted. Returns what [undo] needs.
 */
class MergeContactsUseCase @Inject constructor(
    private val contacts: ContactRepository,
    private val companies: CompanyRepository,
    private val cards: BusinessCardRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock
) {
    suspend operator fun invoke(keepId: String, discardId: String, plan: MergePlan): DomainResult<MergeUndo> {
        if (keepId == discardId) return DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME))
        return transactions.atomically {
            val keep = contacts.get(keepId) ?: return@atomically DomainResult.Failure(DomainError.NotFound(keepId))
            val discard = contacts.get(discardId) ?: return@atomically DomainResult.Failure(DomainError.NotFound(discardId))
            val undo = MergeUndo(
                keptBefore = keep,
                keptEventIds = contacts.eventIds(keepId),
                discarded = discard,
                discardedEventIds = contacts.eventIds(discardId),
                discardedCardIds = cards.observeByContact(discardId).first().map { it.id }
            )
            val companyId = when (val company = companyFor(plan.chosen(MergeField.COMPANY))) {
                is DomainResult.Failure -> return@atomically company
                is DomainResult.Success -> company.value
            }
            val merged = keep.copy(
                firstName = plan.chosen(MergeField.FIRST_NAME).orEmpty(),
                lastName = plan.chosen(MergeField.LAST_NAME).orEmpty(),
                jobTitle = plan.chosen(MergeField.JOB_TITLE),
                companyId = companyId,
                country = plan.chosen(MergeField.COUNTRY),
                phone = plan.chosen(MergeField.PHONE),
                mobile = plan.chosen(MergeField.MOBILE),
                email = plan.chosen(MergeField.EMAIL),
                website = plan.chosen(MergeField.WEBSITE),
                address = plan.chosen(MergeField.ADDRESS),
                notes = plan.mergedNotes(clock.nowMillis()),
                exportedToPhoneAt = listOfNotNull(keep.exportedToPhoneAt, discard.exportedToPhoneAt).maxOrNull()
            )
            (contacts.update(merged) as? DomainResult.Failure)?.let { return@atomically it }
            for (eventId in undo.discardedEventIds) (contacts.addToEvent(keepId, eventId) as? DomainResult.Failure)?.let { return@atomically it }
            for (cardId in undo.discardedCardIds) (cards.attachToContact(cardId, keepId) as? DomainResult.Failure)?.let { return@atomically it }
            (contacts.delete(discardId) as? DomainResult.Failure)?.let { return@atomically it }
            DomainResult.Success(undo)
        }
    }

    /** Puts both contacts back as they were before the merge. */
    suspend fun undo(undo: MergeUndo): DomainResult<Unit> = transactions.atomically {
        (contacts.restore(undo.discarded) as? DomainResult.Failure)?.let { return@atomically it }
        for (eventId in undo.discardedEventIds) (contacts.addToEvent(undo.discarded.id, eventId) as? DomainResult.Failure)?.let { return@atomically it }
        for (cardId in undo.discardedCardIds) (cards.attachToContact(cardId, undo.discarded.id) as? DomainResult.Failure)?.let { return@atomically it }
        (contacts.update(undo.keptBefore) as? DomainResult.Failure)?.let { return@atomically it }
        for (eventId in contacts.eventIds(undo.keptBefore.id) - undo.keptEventIds) {
            (contacts.removeFromEvent(undo.keptBefore.id, eventId) as? DomainResult.Failure)?.let { return@atomically it }
        }
        DomainResult.Success(Unit)
    }

    private suspend fun companyFor(name: String?): DomainResult<String?> {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return DomainResult.Success(null)
        companies.findByName(trimmed)?.let { return DomainResult.Success(it.id) }
        return when (val created = companies.create(CompanyDraft(trimmed))) {
            is DomainResult.Success -> DomainResult.Success(created.value.id)
            is DomainResult.Failure -> created
        }
    }
}

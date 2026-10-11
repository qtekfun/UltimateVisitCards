package com.qtekfun.ultimatevisitcards.domain.usecase

import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.TransactionRunner
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import javax.inject.Inject

/**
 * A reviewed card that belongs to an existing contact (docs/06 §3-5, «Fusionar» / «Añadir como nueva
 * tarjeta»): the contact takes the values chosen in [plan] (existing on the left, the card on the right), the
 * card joins its history with its event, and is marked reviewed. One transaction.
 */
class AddCardToContactUseCase @Inject constructor(
    private val contacts: ContactRepository,
    private val companies: CompanyRepository,
    private val cards: BusinessCardRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock
) {
    suspend operator fun invoke(cardId: String, contactId: String, plan: MergePlan, eventId: String?, sectorId: String?): DomainResult<Contact> =
        transactions.atomically {
            val contact = contacts.get(contactId) ?: return@atomically DomainResult.Failure(DomainError.NotFound(contactId))
            val companyId = when (val company = company(plan.chosen(MergeField.COMPANY), sectorId)) {
                is DomainResult.Failure -> return@atomically company
                is DomainResult.Success -> company.value
            }
            val updated = contacts.update(
                contact.copy(
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
                    notes = plan.mergedNotes(clock.nowMillis())
                )
            )
            if (updated !is DomainResult.Success) return@atomically updated
            listOf(
                suspend { cards.setEvent(cardId, eventId) },
                suspend { cards.attachToContact(cardId, contactId) },
                suspend { cards.updateOcr(cardId, OcrStatus.REVIEWED) }
            ).forEach { step -> (step() as? DomainResult.Failure)?.let { return@atomically it } }
            updated
        }

    private suspend fun company(name: String?, sectorId: String?): DomainResult<String?> {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return DomainResult.Success(null)
        val existing = companies.findByName(trimmed)
        if (existing != null) {
            if (sectorId != null &&
                sectorId != existing.sectorId
            ) {
                (companies.update(existing.copy(sectorId = sectorId)) as? DomainResult.Failure)?.let { return it }
            }
            return DomainResult.Success(existing.id)
        }
        return when (val created = companies.create(CompanyDraft(trimmed, sectorId = sectorId))) {
            is DomainResult.Success -> DomainResult.Success(created.value.id)
            is DomainResult.Failure -> created
        }
    }
}

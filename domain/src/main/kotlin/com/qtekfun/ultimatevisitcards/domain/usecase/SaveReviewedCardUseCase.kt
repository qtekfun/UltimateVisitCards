package com.qtekfun.ultimatevisitcards.domain.usecase

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.TransactionRunner
import javax.inject.Inject

/** What the user confirmed in the review screen. */
data class ReviewedCard(
    val cardId: String,
    val firstName: String,
    val lastName: String,
    val jobTitle: String?,
    val companyName: String?,
    val sectorId: String?,
    val country: String?,
    val phone: String?,
    val mobile: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val notes: String?,
    val eventId: String?
) {
    /** A contact needs at least a name, a company or a way to reach them. */
    val isEmpty: Boolean
        get() = listOf(firstName, lastName, companyName, email, phone, mobile).all { it.isNullOrBlank() }
}

/**
 * Saves a reviewed card as a new contact, in one transaction (docs/01, capture flow):
 * 1. the company is linked by normalised name (`ABC Marine GmbH` = `abc marine`) or created with the card's
 *    sector, country and website; choosing a sector for an existing company updates it;
 * 2. the contact is created;
 * 3. the card gets its event and is linked to the contact (who joins the event; newest card is primary);
 * 4. the card is marked `REVIEWED`.
 * Any failure rolls everything back.
 */
class SaveReviewedCardUseCase @Inject constructor(
    private val companies: CompanyRepository,
    private val contacts: ContactRepository,
    private val cards: BusinessCardRepository,
    private val transactions: TransactionRunner
) {
    suspend operator fun invoke(input: ReviewedCard): DomainResult<Contact> {
        if (input.isEmpty) return DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME))
        return transactions.atomically {
            val companyId = when (val company = company(input)) {
                is DomainResult.Failure -> return@atomically company
                is DomainResult.Success -> company.value
            }
            val contact = contacts.create(
                ContactDraft(
                    firstName = input.firstName,
                    lastName = input.lastName,
                    jobTitle = input.jobTitle,
                    companyId = companyId,
                    country = input.country,
                    phone = input.phone,
                    mobile = input.mobile,
                    email = input.email,
                    website = input.website,
                    address = input.address,
                    notes = input.notes
                )
            )
            if (contact !is DomainResult.Success) return@atomically contact
            listOf(
                suspend { cards.setEvent(input.cardId, input.eventId) },
                suspend { cards.attachToContact(input.cardId, contact.value.id) },
                suspend { cards.updateOcr(input.cardId, OcrStatus.REVIEWED) }
            ).forEach { step -> (step() as? DomainResult.Failure)?.let { return@atomically it } }
            contact
        }
    }

    /** The id of the existing or new company, or null without a company name. */
    private suspend fun company(input: ReviewedCard): DomainResult<String?> {
        val name = input.companyName?.trim().orEmpty()
        if (name.isEmpty()) return DomainResult.Success(null)
        val existing = companies.findByName(name)
        if (existing == null) {
            val created = companies.create(CompanyDraft(name = name, sectorId = input.sectorId, country = input.country, website = input.website))
            return when (created) {
                is DomainResult.Success -> DomainResult.Success(created.value.id)
                is DomainResult.Failure -> created
            }
        }
        if (input.sectorId != null && input.sectorId != existing.sectorId) {
            (companies.update(existing.copy(sectorId = input.sectorId)) as? DomainResult.Failure)?.let { return it }
        }
        return DomainResult.Success(existing.id)
    }
}

package com.qtekfun.ultimatevisitcards.domain.usecase

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.TransactionRunner
import javax.inject.Inject

/** Saves an edited contact; the company is linked by normalised name or created (same rule as the review). */
class UpdateContactUseCase @Inject constructor(
    private val contacts: ContactRepository,
    private val companies: CompanyRepository,
    private val transactions: TransactionRunner,
) {
    suspend operator fun invoke(contact: Contact, companyName: String?): DomainResult<Contact> {
        val reachable = listOf(contact.firstName, contact.lastName, companyName, contact.email, contact.phone, contact.mobile).any { !it.isNullOrBlank() }
        if (!reachable) return DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME))
        return transactions.atomically {
            val name = companyName?.trim().orEmpty()
            val companyId = when {
                name.isEmpty() -> null
                else -> companies.findByName(name)?.id ?: when (val created = companies.create(CompanyDraft(name, country = contact.country))) {
                    is DomainResult.Success -> created.value.id
                    is DomainResult.Failure -> return@atomically created
                }
            }
            contacts.update(contact.copy(companyId = companyId))
        }
    }
}

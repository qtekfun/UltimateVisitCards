package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import kotlinx.coroutines.flow.Flow

data class ContactDraft(
    val firstName: String = "",
    val lastName: String = "",
    val jobTitle: String? = null,
    val companyId: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null,
    val notes: String? = null
)

interface ContactRepository {
    /** All contacts, most recent first. */
    fun observeAll(): Flow<List<Contact>>

    fun observe(id: String): Flow<Contact?>

    suspend fun get(id: String): Contact?

    fun observeByCompany(companyId: String): Flow<List<Contact>>

    fun observeByEvent(eventId: String): Flow<List<Contact>>

    /** Full-text search over every searchable field (docs/02-modelo-datos.md); a blank query returns nothing. */
    fun search(query: String): Flow<List<Contact>>

    suspend fun create(draft: ContactDraft): DomainResult<Contact>

    suspend fun update(contact: Contact): DomainResult<Contact>

    /** Deletes the contact and, by cascade, its cards and event links. */
    suspend fun delete(id: String): DomainResult<Unit>

    suspend fun eventIds(contactId: String): Set<String>

    suspend fun addToEvent(contactId: String, eventId: String): DomainResult<Unit>

    suspend fun removeFromEvent(contactId: String, eventId: String): DomainResult<Unit>

    /** Rebuilds the whole search index (after a restore, or from Settings). */
    suspend fun rebuildSearchIndex()
}

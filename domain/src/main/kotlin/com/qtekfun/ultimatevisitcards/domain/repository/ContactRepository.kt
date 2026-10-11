package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.ContactListItem
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

/** A contact with its company name, as needed to compare it with a new one (duplicates). */
data class ContactWithCompany(val contact: Contact, val companyName: String?)

interface ContactRepository {
    /** All contacts, most recent first. */
    fun observeAll(): Flow<List<Contact>>

    fun observe(id: String): Flow<Contact?>

    suspend fun get(id: String): Contact?

    fun observeByCompany(companyId: String): Flow<List<Contact>>

    fun observeByEvent(eventId: String): Flow<List<Contact>>

    /** Full-text search over every searchable field (docs/02-modelo-datos.md); a blank query returns nothing. */
    fun search(query: String): Flow<List<Contact>>

    /**
     * The contact list for [filter]: all contacts (newest first) when it is empty, otherwise the matches
     * sorted by surname and name.
     */
    fun observeList(filter: ContactFilter): Flow<List<ContactListItem>>

    /** Countries in use (contact or company), for the country filter. */
    fun observeCountries(): Flow<List<String>>

    fun observeCount(): Flow<Int>

    suspend fun create(draft: ContactDraft): DomainResult<Contact>

    suspend fun update(contact: Contact): DomainResult<Contact>

    /** Inserts a contact exactly as given, id and dates included (undoing a merge; restoring a backup). */
    suspend fun restore(contact: Contact): DomainResult<Contact>

    /** Records that the contact was just added to the phone's contacts. */
    suspend fun markExportedToPhone(id: String): DomainResult<Unit>

    /** Deletes the contact and, by cascade, its cards and event links. */
    suspend fun delete(id: String): DomainResult<Unit>

    suspend fun eventIds(contactId: String): Set<String>

    suspend fun addToEvent(contactId: String, eventId: String): DomainResult<Unit>

    suspend fun removeFromEvent(contactId: String, eventId: String): DomainResult<Unit>

    /** Every contact with its company name, for the duplicate check on save. */
    suspend fun allWithCompany(): List<ContactWithCompany>

    /** Rebuilds the whole search index (after a restore, or from Settings). */
    suspend fun rebuildSearchIndex()
}

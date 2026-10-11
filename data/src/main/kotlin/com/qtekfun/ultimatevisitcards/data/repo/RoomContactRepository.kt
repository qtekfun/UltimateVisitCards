package com.qtekfun.ultimatevisitcards.data.repo

import androidx.room3.withWriteTransaction
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.data.db.toEntity
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.search.SearchQuery
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomContactRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: SearchIndexer,
    private val clock: Clock,
    private val ids: IdGenerator
) : ContactRepository {
    private val dao = db.contactDao()

    override fun observeAll(): Flow<List<Contact>> = dao.observeAll().toDomain()

    override fun observe(id: String): Flow<Contact?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Contact? = dao.get(id)?.toDomain()

    override fun observeByCompany(companyId: String): Flow<List<Contact>> = dao.observeByCompany(companyId).toDomain()

    override fun observeByEvent(eventId: String): Flow<List<Contact>> = dao.observeByEvent(eventId).toDomain()

    override fun search(query: String): Flow<List<Contact>> {
        val match = SearchQuery.toFtsMatch(query) ?: return flowOf(emptyList())
        return dao.search(match).toDomain()
    }

    override suspend fun create(draft: ContactDraft): DomainResult<Contact> = db.writeResult {
        val now = clock.nowMillis()
        val contact = Contact(
            id = ids.newId(),
            firstName = draft.firstName.trim(),
            lastName = draft.lastName.trim(),
            jobTitle = draft.jobTitle.clean(),
            companyId = draft.companyId,
            country = draft.country.clean(),
            phone = draft.phone.clean(),
            mobile = draft.mobile.clean(),
            email = draft.email.clean()?.lowercase(),
            website = draft.website.clean(),
            address = draft.address.clean(),
            notes = draft.notes.clean(),
            createdAt = now,
            updatedAt = now
        )
        dao.insert(contact.toEntity())
        indexer.reindex(contact.id)
        contact.success()
    }

    override suspend fun update(contact: Contact): DomainResult<Contact> = db.writeResult {
        val current = dao.get(contact.id) ?: return@writeResult failure(DomainError.NotFound(contact.id))
        val updated = contact.copy(
            firstName = contact.firstName.trim(),
            lastName = contact.lastName.trim(),
            jobTitle = contact.jobTitle.clean(),
            country = contact.country.clean(),
            phone = contact.phone.clean(),
            mobile = contact.mobile.clean(),
            email = contact.email.clean()?.lowercase(),
            website = contact.website.clean(),
            address = contact.address.clean(),
            notes = contact.notes.clean(),
            createdAt = current.createdAt,
            updatedAt = clock.nowMillis()
        )
        dao.update(updated.toEntity())
        indexer.reindex(updated.id)
        updated.success()
    }

    override suspend fun delete(id: String): DomainResult<Unit> = db.writeResult {
        db.searchDao().delete(id)
        if (dao.delete(id) == 0) return@writeResult failure(DomainError.NotFound(id))
        Unit.success()
    }

    override suspend fun eventIds(contactId: String): Set<String> = dao.eventIds(contactId).toSet()

    override suspend fun addToEvent(contactId: String, eventId: String): DomainResult<Unit> = db.writeResult {
        dao.get(contactId) ?: return@writeResult failure(DomainError.NotFound(contactId))
        db.eventDao().get(eventId) ?: return@writeResult failure(DomainError.NotFound(eventId))
        dao.addToEvent(ContactEventEntity(contactId, eventId))
        indexer.reindex(contactId)
        Unit.success()
    }

    override suspend fun removeFromEvent(contactId: String, eventId: String): DomainResult<Unit> = db.writeResult {
        dao.removeFromEvent(contactId, eventId)
        indexer.reindex(contactId)
        Unit.success()
    }

    override suspend fun rebuildSearchIndex() {
        db.withWriteTransaction { indexer.rebuild() }
    }

    private fun Flow<List<com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity>>.toDomain(): Flow<List<Contact>> =
        map { list -> list.map { it.toDomain() } }
}

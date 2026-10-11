package com.qtekfun.ultimatevisitcards.data.repo

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.data.db.toEntity
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEventRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: SearchIndexer,
    private val clock: Clock,
    private val ids: IdGenerator
) : EventRepository {
    private val dao = db.eventDao()

    override fun observeAll(): Flow<List<EventSummary>> = dao.observeAllWithCount().map { list ->
        list.map { EventSummary(it.event.toDomain(), it.contactCount) }
    }

    override fun observe(id: String): Flow<Event?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Event? = dao.get(id)?.toDomain()

    override suspend fun create(draft: EventDraft): DomainResult<Event> = db.writeResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return@writeResult failure(DomainError.Invalid(DomainError.Field.NAME))
        val event = Event(
            id = ids.newId(),
            name = name,
            location = draft.location.clean(),
            startDate = draft.startDate,
            endDate = draft.endDate,
            notes = draft.notes.clean(),
            createdAt = clock.nowMillis()
        )
        dao.insert(event.toEntity())
        event.success()
    }

    override suspend fun update(event: Event): DomainResult<Event> = db.writeResult {
        val current = dao.get(event.id) ?: return@writeResult failure(DomainError.NotFound(event.id))
        val name = event.name.trim()
        if (name.isEmpty()) return@writeResult failure(DomainError.Invalid(DomainError.Field.NAME))
        val updated = event.copy(
            name = name,
            location = event.location.clean(),
            notes = event.notes.clean(),
            createdAt = current.createdAt
        )
        dao.update(updated.toEntity())
        if (current.name != updated.name) indexer.reindex(db.contactDao().contactIdsOfEvent(event.id))
        updated.success()
    }

    override suspend fun delete(id: String): DomainResult<Unit> = db.writeResult {
        val affected = db.contactDao().contactIdsOfEvent(id)
        if (dao.delete(id) == 0) return@writeResult failure(DomainError.NotFound(id))
        indexer.reindex(affected)
        Unit.success()
    }
}

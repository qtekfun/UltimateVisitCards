package com.qtekfun.ultimatevisitcards.data.repo

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.data.db.toEntity
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBusinessCardRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: SearchIndexer,
    private val clock: Clock,
    private val ids: IdGenerator
) : BusinessCardRepository {
    private val dao = db.businessCardDao()
    private val awaitingReview = OcrStatus.AWAITING_REVIEW.map { it.name }

    override suspend fun get(id: String): BusinessCard? = dao.get(id)?.toDomain()

    override fun observe(id: String): Flow<BusinessCard?> = dao.observe(id).map { it?.toDomain() }

    override fun observeByContact(contactId: String): Flow<List<BusinessCard>> = dao.observeByContact(contactId).map { list -> list.map { it.toDomain() } }

    override fun observeAwaitingReview(): Flow<List<BusinessCard>> = dao.observeWithoutContact(awaitingReview).map { list -> list.map { it.toDomain() } }

    override fun observeAwaitingReviewCount(): Flow<Int> = dao.observeCountWithoutContact(awaitingReview)

    override suspend fun create(draft: BusinessCardDraft, id: String?): DomainResult<BusinessCard> = db.writeResult {
        val eventId = draft.eventId
        if (eventId != null && db.eventDao().get(eventId) == null) {
            return@writeResult failure(DomainError.NotFound(eventId))
        }
        val card = BusinessCard(
            id = id ?: ids.newId(),
            eventId = draft.eventId,
            frontOriginal = draft.frontOriginal,
            frontProcessed = draft.frontProcessed,
            backOriginal = draft.backOriginal,
            backProcessed = draft.backProcessed,
            frontQuad = draft.frontQuad,
            backQuad = draft.backQuad,
            ocrStatus = OcrStatus.PENDING,
            capturedAt = draft.capturedAt,
            createdAt = clock.nowMillis()
        )
        dao.insert(card.toEntity())
        card.success()
    }

    override suspend fun updateOcr(id: String, status: OcrStatus, rawOcrText: String?, extractedJson: String?, ocrEngine: String?): DomainResult<Unit> =
        db.writeResult {
            if (dao.updateOcr(id, status.name, rawOcrText, extractedJson, ocrEngine) == 0) {
                return@writeResult failure(DomainError.NotFound(id))
            }
            // The OCR text of a reviewed card is searchable.
            dao.get(id)?.contactId?.let { indexer.reindex(it) }
            Unit.success()
        }

    override suspend fun attachToContact(cardId: String, contactId: String): DomainResult<Unit> = db.writeResult {
        val card = dao.get(cardId) ?: return@writeResult failure(DomainError.NotFound(cardId))
        db.contactDao().get(contactId) ?: return@writeResult failure(DomainError.NotFound(contactId))
        val previousContact = card.contactId
        dao.setContact(cardId, contactId)
        // The contact belongs to the events of all its cards (docs/06-duplicados-historico.md).
        card.eventId?.let { db.contactDao().addToEvent(ContactEventEntity(contactId, it)) }
        refreshPrimary(contactId)
        indexer.reindex(contactId)
        if (previousContact != null && previousContact != contactId) {
            refreshPrimary(previousContact)
            indexer.reindex(previousContact)
        }
        Unit.success()
    }

    override suspend fun setPrimary(cardId: String): DomainResult<Unit> = db.writeResult {
        val card = dao.get(cardId) ?: return@writeResult failure(DomainError.NotFound(cardId))
        val contactId = card.contactId ?: return@writeResult Unit.success()
        dao.setPrimary(contactId, cardId)
        Unit.success()
    }

    override suspend fun delete(id: String): DomainResult<Unit> = db.writeResult {
        val card = dao.get(id) ?: return@writeResult failure(DomainError.NotFound(id))
        val contactId = card.contactId
        if (contactId != null && dao.byContact(contactId).size == 1) {
            return@writeResult failure(DomainError.NotAllowed(DomainError.Rule.ONLY_CARD_OF_CONTACT))
        }
        dao.delete(id)
        if (contactId != null) {
            if (card.isPrimary) refreshPrimary(contactId)
            indexer.reindex(contactId)
        }
        Unit.success()
    }

    /** The newest card becomes the primary one (docs/02-modelo-datos.md). */
    private suspend fun refreshPrimary(contactId: String) {
        dao.byContact(contactId).firstOrNull()?.let { dao.setPrimary(contactId, it.id) }
    }
}

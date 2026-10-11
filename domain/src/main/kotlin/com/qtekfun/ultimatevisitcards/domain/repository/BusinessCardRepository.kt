package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import kotlinx.coroutines.flow.Flow

data class BusinessCardDraft(
    val frontOriginal: String,
    val frontProcessed: String,
    val backOriginal: String? = null,
    val backProcessed: String? = null,
    val frontQuad: String? = null,
    val backQuad: String? = null,
    val eventId: String? = null,
    val capturedAt: Long
)

interface BusinessCardRepository {
    suspend fun get(id: String): BusinessCard?

    fun observe(id: String): Flow<BusinessCard?>

    /** Cards of a contact, newest capture first. */
    fun observeByContact(contactId: String): Flow<List<BusinessCard>>

    /** Cards without contact still waiting for OCR or review, oldest first (the review queue). */
    fun observeAwaitingReview(): Flow<List<BusinessCard>>

    /** «Pending review: N» on the home screen. */
    fun observeAwaitingReviewCount(): Flow<Int>

    /** Saves a freshly captured card in `PENDING`, before it has a contact. */
    suspend fun create(draft: BusinessCardDraft, id: String? = null): DomainResult<BusinessCard>

    suspend fun updateOcr(
        id: String,
        status: OcrStatus,
        rawOcrText: String? = null,
        extractedJson: String? = null,
        ocrEngine: String? = null
    ): DomainResult<Unit>

    /**
     * Links the card to a contact: the contact joins the card's event, the newest card becomes the primary
     * one and the contact's search entry is rebuilt.
     */
    suspend fun attachToContact(cardId: String, contactId: String): DomainResult<Unit>

    suspend fun setPrimary(cardId: String): DomainResult<Unit>

    /** Where the card was obtained; for a linked card the contact joins that event too. */
    suspend fun setEvent(cardId: String, eventId: String?): DomainResult<Unit>

    /** Deletes a card. The only card of a contact cannot be deleted on its own (delete the contact instead). */
    suspend fun delete(id: String): DomainResult<Unit>
}

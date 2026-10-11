package com.qtekfun.ultimatevisitcards.work

import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Keeps the OCR queue honest across process deaths (docs/04 §4):
 * - at start-up, a card left in `PROCESSING` (the app died mid-OCR) goes back to `PENDING`, and every
 *   `PENDING` card is queued again (the worker is idempotent, so a card already queued is read once);
 * - [retry] puts a card in `OCR_ERROR` back through the queue.
 */
class OcrRecovery @Inject constructor(private val cards: BusinessCardRepository, private val queue: OcrScheduler) {
    suspend fun resume(): Int {
        val waiting = cards.observeAwaitingReview().first().filter { it.ocrStatus == OcrStatus.PROCESSING || it.ocrStatus == OcrStatus.PENDING }
        waiting.filter { it.ocrStatus == OcrStatus.PROCESSING }.forEach { cards.updateOcr(it.id, OcrStatus.PENDING) }
        waiting.forEach { queue.enqueue(it.id) }
        return waiting.size
    }

    suspend fun retry(cardId: String): DomainResult<Unit> {
        val result = cards.updateOcr(cardId, OcrStatus.PENDING)
        if (result is DomainResult.Success) queue.enqueue(cardId)
        return result
    }
}

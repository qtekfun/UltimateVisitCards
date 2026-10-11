package com.qtekfun.ultimatevisitcards.ui.capture

import android.graphics.Bitmap
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.util.DefaultDispatcher
import com.qtekfun.ultimatevisitcards.ocr.processing.CardImageProcessor
import com.qtekfun.ultimatevisitcards.work.OcrScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One photographed side, already on disk. */
data class CapturedSide(
    val side: CardSide,
    val originalPath: String,
    val processedPath: String,
    val quad: CardQuad,
    /** False when the card edges were not found and the guide was used. */
    val detected: Boolean,
    /** The straightened image, for the confirmation preview. */
    val preview: Bitmap
)

/**
 * Turns photos into a `BusinessCard(PENDING)` (docs/01-arquitectura.md, capture flow): every photo is saved
 * to disk as soon as it is taken (original + straightened), so nothing is lost; the database row is written
 * last. If that fails, the card's files are removed.
 */
class CardCaptureSaver @Inject constructor(
    private val store: ImageStore,
    private val processor: CardImageProcessor,
    private val cards: BusinessCardRepository,
    private val ocrQueue: OcrScheduler,
    @DefaultDispatcher private val default: CoroutineDispatcher
) {
    /** Saves the original, straightens it and saves the result. [photo] must be upright. */
    suspend fun saveSide(cardId: String, side: CardSide, photo: Bitmap): CapturedSide {
        val originalPath = store.saveOriginal(cardId, side, photo)
        val result = withContext(default) { processor.process(photo) }
        val processedPath = store.saveProcessed(cardId, side, result.card)
        return CapturedSide(side, originalPath, processedPath, result.quad, result.detected, result.card)
    }

    /** Re-crops a side from its original with corners chosen by hand. */
    suspend fun recrop(cardId: String, captured: CapturedSide, quad: CardQuad): CapturedSide {
        val original = store.load(captured.originalPath) ?: throw java.io.IOException("Original missing: ${captured.originalPath}")
        val card = withContext(default) { processor.crop(original, quad) }
        val processedPath = store.saveProcessed(cardId, captured.side, card)
        return captured.copy(processedPath = processedPath, quad = quad, detected = true, preview = card)
    }

    /** Loads a side's original photo for manual cropping. */
    suspend fun original(captured: CapturedSide): Bitmap? = store.load(captured.originalPath, MANUAL_CROP_MAX_SIDE)

    /** Writes the card row in `PENDING` and queues its OCR. On failure the card's images are deleted. */
    suspend fun commit(cardId: String, front: CapturedSide, back: CapturedSide?, eventId: String?, capturedAt: Long): DomainResult<BusinessCard> {
        val draft = BusinessCardDraft(
            frontOriginal = front.originalPath,
            frontProcessed = front.processedPath,
            backOriginal = back?.originalPath,
            backProcessed = back?.processedPath,
            frontQuad = front.quad.toJson(),
            backQuad = back?.quad?.toJson(),
            eventId = eventId,
            capturedAt = capturedAt
        )
        val result = cards.create(draft, id = cardId)
        when (result) {
            is DomainResult.Success -> ocrQueue.enqueue(cardId)
            is DomainResult.Failure -> store.deleteCard(cardId)
        }
        return result
    }

    /** Drops a capture the user abandoned. */
    suspend fun discard(cardId: String) = store.deleteCard(cardId)

    /** Removes a side that was photographed and then rejected (e.g. retake the back). */
    suspend fun discardSide(cardId: String, side: CardSide) = store.deleteSide(cardId, side)

    companion object {
        private const val MANUAL_CROP_MAX_SIDE = 1600
    }
}

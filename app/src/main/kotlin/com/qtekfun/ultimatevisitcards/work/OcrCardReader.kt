package com.qtekfun.ultimatevisitcards.work

import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.extract.CardFieldExtractor
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Reads one card: OCR of the straightened sides, field extraction, and the card moves on
 * `PENDING → PROCESSING → PENDING_REVIEW`. Idempotent: a card that is no longer waiting is skipped. A failure
 * (any error of the engine, or no answer within [OCR_TIMEOUT_MILLIS]) is retried once; the second one leaves
 * the card in `OCR_ERROR` with its photos untouched, to be filled in by hand (docs/04 §4). If the reading is
 * cancelled, the card goes back to `PENDING`; a process death is undone by [OcrRecovery] at the next start.
 */
class OcrCardReader @Inject constructor(
    private val cards: BusinessCardRepository,
    private val store: ImageStore,
    private val ocr: OcrService,
    private val extractor: CardFieldExtractor
) {
    suspend fun read(cardId: String) {
        val card = cards.get(cardId) ?: return // deleted meanwhile
        if (card.ocrStatus !in WAITING) return
        try {
            cards.updateOcr(cardId, OcrStatus.PROCESSING)
            repeat(ATTEMPTS) {
                if (attempt(card)) return
                // A hung or broken engine is discarded: the next attempt gets a fresh one.
                withContext(NonCancellable) { ocr.release() }
            }
            cards.updateOcr(cardId, OcrStatus.OCR_ERROR, ocrEngine = ocr.engineId)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cards.updateOcr(cardId, OcrStatus.PENDING) }
            throw e
        }
    }

    /** True when the card was read; false when the engine failed or did not answer in time. */
    private suspend fun attempt(card: BusinessCard): Boolean = try {
        withTimeout(OCR_TIMEOUT_MILLIS) { readOnce(card) }
        true
    } catch (e: TimeoutCancellationException) {
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Any engine failure (OcrException, IOException, a native library that does not load, out of
        // memory…) must end in a state the user can act on, never in an endless «Reading…».
        false
    }

    private suspend fun readOnce(card: BusinessCard) {
        val front = ocr.recognize(OcrImage(store.file(card.frontProcessed)))
        val back = card.backProcessed?.let { ocr.recognize(OcrImage(store.file(it))) }
        val fields = extractor.extract(front, back)
        val raw = listOfNotNull(front.text, back?.text).filter { it.isNotBlank() }.joinToString("\n\n")
        cards.updateOcr(card.id, OcrStatus.PENDING_REVIEW, rawOcrText = raw, extractedJson = fields.toJson(), ocrEngine = ocr.engineId)
    }

    companion object {
        /** Two failures in total → OCR_ERROR. */
        const val ATTEMPTS = 2

        /** Both sides of a card take a few seconds; no answer in this time means the engine hung. */
        const val OCR_TIMEOUT_MILLIS = 120_000L

        /** OCR_ERROR is included so a manual «retry» can put the card back through. */
        private val WAITING = setOf(OcrStatus.PENDING, OcrStatus.PROCESSING, OcrStatus.OCR_ERROR)
    }
}

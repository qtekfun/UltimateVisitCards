package com.qtekfun.ultimatevisitcards.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.extract.CardFieldExtractor
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrException
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Reads one card: OCR of the straightened sides, field extraction, and the card moves on
 * `PENDING → PROCESSING → PENDING_REVIEW`. Idempotent: a card that is no longer waiting is skipped. A failure
 * (any error of the engine, or no answer within [OCR_TIMEOUT_MILLIS]) is retried once; the second one leaves
 * the card in `OCR_ERROR` with its photos untouched, to be filled in by hand (docs/04 §4). A card is never left
 * in `PROCESSING`: if the system stops the worker, it goes back to `PENDING`.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cards: BusinessCardRepository,
    private val store: ImageStore,
    private val ocr: OcrService,
    private val extractor: CardFieldExtractor,
    private val queue: OcrQueue
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cardId = inputData.getString(KEY_CARD_ID) ?: return Result.failure()
        val card = cards.get(cardId) ?: return Result.success() // deleted meanwhile
        if (card.ocrStatus !in WAITING) return Result.success()
        cards.updateOcr(cardId, OcrStatus.PROCESSING)
        return try {
            withTimeout(OCR_TIMEOUT_MILLIS) { read(card) }
            Result.success()
        } catch (e: TimeoutCancellationException) {
            failed(cardId)
        } catch (e: CancellationException) {
            // Stopped by the system: WorkManager runs it again later.
            withContext(NonCancellable) { cards.updateOcr(cardId, OcrStatus.PENDING) }
            throw e
        } catch (e: Throwable) {
            // Any engine failure (OcrException, IOException, a native library that does not load, out of
            // memory…) must end in a state the user can act on, never in an endless «Reading…».
            failed(cardId)
        } finally {
            if (queue.isDrained()) ocr.release()
        }
    }

    private suspend fun read(card: BusinessCard) {
        val front = ocr.recognize(OcrImage(store.file(card.frontProcessed)))
        val back = card.backProcessed?.let { ocr.recognize(OcrImage(store.file(it))) }
        val fields = extractor.extract(front, back)
        val raw = listOfNotNull(front.text, back?.text).filter { it.isNotBlank() }.joinToString("\n\n")
        cards.updateOcr(card.id, OcrStatus.PENDING_REVIEW, rawOcrText = raw, extractedJson = fields.toJson(), ocrEngine = ocr.engineId)
    }

    private suspend fun failed(cardId: String): Result = withContext(NonCancellable) {
        // A hung or broken engine is discarded: the next card gets a fresh one.
        ocr.release()
        if (runAttemptCount < MAX_RETRIES) {
            cards.updateOcr(cardId, OcrStatus.PENDING)
            Result.retry()
        } else {
            cards.updateOcr(cardId, OcrStatus.OCR_ERROR, ocrEngine = ocr.engineId)
            // Success for WorkManager: the card is handled (by hand), and the queue goes on.
            Result.success()
        }
    }

    companion object {
        const val KEY_CARD_ID = "cardId"

        /** One retry: two failures in total → OCR_ERROR. */
        const val MAX_RETRIES = 1

        /** Both sides of a card take a few seconds; no answer in this time means the engine hung. */
        const val OCR_TIMEOUT_MILLIS = 120_000L

        /** OCR_ERROR is included so a manual «retry» (phase 5) can put the card back through. */
        private val WAITING = setOf(OcrStatus.PENDING, OcrStatus.PROCESSING, OcrStatus.OCR_ERROR)
    }
}

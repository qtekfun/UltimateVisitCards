package com.qtekfun.ultimatevisitcards.work

import com.qtekfun.ultimatevisitcards.di.ApplicationScope
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The OCR queue (docs/04 §4): cards are read one at a time, in order, inside the app process, so recognition
 * never runs in parallel (Tesseract is memory hungry) and starts as soon as the card is saved. It does not
 * depend on the system job scheduler, which some vendors (ColorOS, MIUI…) defer for many minutes. The queue
 * lives in memory; the card's state in the database is what persists, and [OcrRecovery] queues again at
 * start-up whatever was left waiting. The engine is released when the queue runs dry.
 */
@Singleton
class OcrQueue @Inject constructor(private val reader: OcrCardReader, private val ocr: OcrService, @ApplicationScope scope: CoroutineScope) : OcrScheduler {
    private val cards = Channel<String>(Channel.UNLIMITED)
    private val waiting = AtomicInteger()

    init {
        scope.launch {
            for (cardId in cards) {
                try {
                    reader.read(cardId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The card stays as it was (OcrRecovery picks it up again); the queue goes on.
                }
                if (waiting.decrementAndGet() == 0) ocr.release()
            }
        }
    }

    override fun enqueue(cardId: String) {
        waiting.incrementAndGet()
        cards.trySend(cardId)
    }
}

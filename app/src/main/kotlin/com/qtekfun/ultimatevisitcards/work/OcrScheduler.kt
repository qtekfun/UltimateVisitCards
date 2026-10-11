package com.qtekfun.ultimatevisitcards.work

/** Puts a card in the OCR queue. [OcrQueue] in the app; a recording fake in tests. */
fun interface OcrScheduler {
    fun enqueue(cardId: String)
}

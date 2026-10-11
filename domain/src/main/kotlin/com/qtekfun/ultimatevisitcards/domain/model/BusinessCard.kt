package com.qtekfun.ultimatevisitcards.domain.model

/** A photographed card. It exists before its contact: [contactId] is null until the card is reviewed. */
data class BusinessCard(
    val id: String,
    val contactId: String? = null,
    val eventId: String? = null,
    /** Paths relative to the app's private storage. */
    val frontOriginal: String,
    val frontProcessed: String,
    val backOriginal: String? = null,
    val backProcessed: String? = null,
    /** Detected corners (JSON) to re-crop from the original. */
    val frontQuad: String? = null,
    val backQuad: String? = null,
    val rawOcrText: String = "",
    val extractedJson: String? = null,
    val ocrEngine: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.PENDING,
    val isPrimary: Boolean = false,
    val capturedAt: Long,
    val createdAt: Long
)

enum class OcrStatus {
    PENDING,
    PROCESSING,
    PENDING_REVIEW,
    REVIEWED,
    OCR_ERROR
    ;

    companion object {
        /** States that count as «pending review» for the home banner (docs/02-modelo-datos.md). */
        val AWAITING_REVIEW: Set<OcrStatus> = setOf(PENDING, PROCESSING, PENDING_REVIEW, OCR_ERROR)
    }
}

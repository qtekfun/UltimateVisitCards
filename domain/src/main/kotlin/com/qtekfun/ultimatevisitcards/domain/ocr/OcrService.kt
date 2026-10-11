package com.qtekfun.ultimatevisitcards.domain.ocr

import java.io.File
import kotlinx.serialization.Serializable

/**
 * Text recognition engine, bound per flavour (`play` → ML Kit, `foss` → Tesseract). The field extractor and
 * its tests never know which engine produced a result (docs/04-ocr-extraccion.md).
 */
interface OcrService {
    /** `mlkit` | `tesseract`, stored in `BusinessCard.ocrEngine`. */
    val engineId: String

    /** Recognises the straightened card image. Throws [OcrException] when the engine fails. */
    suspend fun recognize(image: OcrImage, hints: OcrHints = OcrHints()): OcrResult

    /** Frees engine resources at the end of a queue run; the next [recognize] sets them up again. */
    suspend fun release() {}

    companion object {
        const val MLKIT = "mlkit"
        const val TESSERACT = "tesseract"
    }
}

/** The image to recognise: a JPEG in the app's private storage. */
data class OcrImage(val file: File)

/** Recognition hints; engines may ignore them. */
data class OcrHints(val languages: List<String> = listOf("eng", "spa", "fra", "deu", "ita"))

class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A rectangle in normalised image coordinates (0..1), so the extractor does not depend on the engine. */
@Serializable
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2
}

@Serializable
data class OcrResult(val text: String, val blocks: List<OcrBlock>) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }

    companion object {
        val EMPTY = OcrResult("", emptyList())
    }
}

@Serializable
data class OcrBlock(val text: String, val lines: List<OcrLine>, val box: NormRect)

/**
 * One recognised line. [confidence] is 0..1 or null when the engine does not give one (unknown).
 * [heightPx] is the line height in pixels of the recognised image: bigger text weighs more for names and
 * companies.
 */
@Serializable
data class OcrLine(val text: String, val box: NormRect, val confidence: Float?, val heightPx: Float)

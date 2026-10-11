package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The rectangular guide drawn over the preview: a centred 85×55 landscape card covering most of the frame.
 * The same rectangle, in image coordinates, is the fallback crop when no card is detected, so what the user
 * framed is what gets kept.
 */
object CaptureGuide {
    const val CARD_ASPECT = 85.0 / 55.0
    private const val FILL = 0.88

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun rectFor(width: Int, height: Int): Rect {
        var w = width * FILL
        var h = w / CARD_ASPECT
        if (h > height * FILL) {
            h = height * FILL
            w = h * CARD_ASPECT
        }
        val left = ((width - w) / 2).roundToInt()
        val top = ((height - h) / 2).roundToInt()
        return Rect(left, top, min(width, left + w.roundToInt()), min(height, top + h.roundToInt()))
    }
}

package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OCR input for Tesseract (docs/04 §1.5): rescaled to about 300 dpi for a card (85 mm ≈ 1000 px; a bit
 * more for small print) and binarised with a local (adaptive) threshold, which is the contrast enhancement:
 * each pixel is compared with its neighbourhood, so uneven lighting does not erase text. A global contrast
 * stretch is not used: it clips the dark side of a badly lit card and loses its text. ML Kit gets the
 * straightened colour image as is.
 */
object ImagePreprocessor {
    /** Long side of the OCR input: ~360 dpi for an 85 mm card. */
    const val TARGET_LONG_SIDE = 1200
    private const val WINDOW_FRACTION = 1.0 / 16 // window ≈ a line of text tall
    private const val BIAS = 0.15 // darker than its neighbourhood by 15 % → ink
    private const val MIN_CONTRAST = 12 // …and by at least this many grey levels (noise in dark areas is not ink)

    fun forTesseract(card: ArgbImage): LumaImage = binarize(resize(LumaImage.fromArgb(card), TARGET_LONG_SIDE))

    /** Bilinear resize so the long side is [longSide] (up or down). */
    internal fun resize(image: LumaImage, longSide: Int): LumaImage {
        val scale = longSide.toDouble() / max(image.width, image.height)
        if (scale in 0.98..1.02) return image
        val w = max(1, (image.width * scale).roundToInt())
        val h = max(1, (image.height * scale).roundToInt())
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sy = ((y + 0.5) / scale - 0.5).coerceIn(0.0, image.height - 1.0)
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, image.height - 1)
            val fy = sy - y0
            for (x in 0 until w) {
                val sx = ((x + 0.5) / scale - 0.5).coerceIn(0.0, image.width - 1.0)
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, image.width - 1)
                val fx = sx - x0
                val v = image[x0, y0] * (1 - fx) * (1 - fy) + image[x1, y0] * fx * (1 - fy) + image[x0, y1] * (1 - fx) * fy + image[x1, y1] * fx * fy
                out[y * w + x] = v.roundToInt()
            }
        }
        return LumaImage(w, h, out)
    }

    /** Bradley-Roth adaptive threshold with an integral image: 0 = ink, 255 = paper. */
    internal fun binarize(image: LumaImage): LumaImage {
        val w = image.width
        val h = image.height
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += image.data[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + row
            }
        }
        val half = max(4, (max(w, h) * WINDOW_FRACTION / 2).roundToInt())
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - half)
            val y1 = minOf(h, y + half + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - half)
                val x1 = minOf(w, x + half + 1)
                val sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = sum.toDouble() / ((x1 - x0) * (y1 - y0))
                val v = image.data[y * w + x]
                out[y * w + x] = if (v < mean * (1 - BIAS) && mean - v >= MIN_CONTRAST) 0 else 255
            }
        }
        return LumaImage(w, h, out)
    }
}

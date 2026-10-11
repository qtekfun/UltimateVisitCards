package com.qtekfun.ultimatevisitcards.ocr.processing

import android.graphics.Bitmap
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.NormPoint
import javax.inject.Inject

/**
 * Turns an upright photo into the straightened card (docs/04-ocr-extraccion.md, step 3-4): detect the card
 * and straighten it, or crop by the capture guide when nothing reliable is found. Also re-crops from a
 * quad chosen by hand.
 */
class CardImageProcessor @Inject constructor() {
    private val detector = CardDetector()

    data class Result(
        val card: Bitmap,
        /** Corners used, normalised to the photo; also stored when they come from the guide. */
        val quad: CardQuad,
        /** False when the guide was used: the review screen offers manual cropping. */
        val detected: Boolean
    )

    fun process(photo: Bitmap): Result {
        val argb = Bitmaps.toArgb(photo)
        val detection = detector.detect(LumaImage.fromArgb(argb))
        val quad = detection?.quad ?: guideQuad(photo.width, photo.height)
        return Result(
            card = Bitmaps.toBitmap(PerspectiveTransform.warp(argb, quad, PROCESSED_MAX_SIDE)),
            quad = quad.normalised(photo.width, photo.height),
            detected = detection != null
        )
    }

    /** Straightens [photo] with corners chosen by hand (manual crop). */
    fun crop(photo: Bitmap, quad: CardQuad): Bitmap {
        val pixels = Quad.ordered(quad.points.map { Point(it.x.toDouble() * photo.width, it.y.toDouble() * photo.height) })
        return Bitmaps.toBitmap(PerspectiveTransform.warp(Bitmaps.toArgb(photo), pixels, PROCESSED_MAX_SIDE))
    }

    companion object {
        /** Long side of the straightened image (docs/04: ≤ 1600 px). */
        const val PROCESSED_MAX_SIDE = 1600

        fun guideQuad(width: Int, height: Int): Quad {
            val r = CaptureGuide.rectFor(width, height)
            return Quad(
                Point(r.left.toDouble(), r.top.toDouble()),
                Point(r.right.toDouble(), r.top.toDouble()),
                Point(r.right.toDouble(), r.bottom.toDouble()),
                Point(r.left.toDouble(), r.bottom.toDouble())
            )
        }

        fun Quad.normalised(width: Int, height: Int): CardQuad = CardQuad.of(
            points.map { NormPoint((it.x / width).toFloat().coerceIn(0f, 1f), (it.y / height).toFloat().coerceIn(0f, 1f)) }
        )
    }
}

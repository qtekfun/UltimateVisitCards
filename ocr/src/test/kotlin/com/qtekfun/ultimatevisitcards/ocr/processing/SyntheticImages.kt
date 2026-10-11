package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Generated test photos of a card on a table: no real photos or personal data in the repo. */
object SyntheticImages {
    /** The corners of an 85×55 card of [cardWidth] px, rotated by [degrees] around ([cx], [cy]). */
    fun rotatedCard(cx: Double, cy: Double, cardWidth: Double, degrees: Double): Quad {
        val h = cardWidth * 55 / 85
        val a = Math.toRadians(degrees)
        val corners = listOf(-cardWidth / 2 to -h / 2, cardWidth / 2 to -h / 2, cardWidth / 2 to h / 2, -cardWidth / 2 to h / 2)
        return Quad.ordered(corners.map { (x, y) -> Point(cx + x * cos(a) - y * sin(a), cy + x * sin(a) + y * cos(a)) })
    }

    /**
     * A [width]×[height] luma photo: textured background of [background] luma, the card of [card] luma inside
     * [quad] with dark «text» lines, and Gaussian-ish noise.
     */
    fun photo(width: Int, height: Int, quad: Quad?, card: Int = 225, background: Int = 70, text: Int = 40, noise: Int = 12, seed: Int = 1): LumaImage {
        val random = Random(seed)
        // Card space (0..1, 0..1) for every image pixel, to place the text lines on the card.
        val toCard = quad?.let {
            PerspectiveTransform.homography(it.points, listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0), Point(0.0, 1.0)))
        }
        val data = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Wood-like texture plus a lighting gradient.
                var v = background + ((x / 7 + y / 11) % 5) * 3 + (x * 20 / width)
                if (toCard != null) {
                    val px = x + 0.5
                    val py = y + 0.5
                    val d = toCard[6] * px + toCard[7] * py + 1
                    val u = (toCard[0] * px + toCard[1] * py + toCard[2]) / d
                    val w = (toCard[3] * px + toCard[4] * py + toCard[5]) / d
                    if (u in 0.0..1.0 && w in 0.0..1.0) {
                        v = card
                        val line = ((w - 0.15) / 0.12).toInt()
                        val inLine = w > 0.15 && w < 0.85 && ((w - 0.15) / 0.12 - line) < 0.45
                        if (inLine && u > 0.1 && u < 0.3 + 0.5 * ((line * 37) % 10) / 10.0) v = text
                    }
                }
                data[y * width + x] = (v + random.nextInt(-noise, noise + 1)).coerceIn(0, 255)
            }
        }
        return LumaImage(width, height, data)
    }

    fun argbFromLuma(image: LumaImage): ArgbImage = ArgbImage(
        image.width,
        image.height,
        IntArray(image.data.size) { i -> image.data[i].let { (0xFF shl 24) or (it shl 16) or (it shl 8) or it } }
    )
}

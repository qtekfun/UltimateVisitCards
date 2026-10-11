package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.max
import kotlin.math.min

/**
 * Finds a business card in a photo: the largest region that contrasts with its surroundings and has the
 * shape of a card (docs/04-ocr-extraccion.md).
 *
 * 1. Downscale to [workSize] and blur.
 * 2. Otsu threshold, tried in both polarities (light card on dark table and the opposite).
 * 3. Morphological opening (detach from touching objects) and closing (fill the text).
 * 4. Connected components; for each large one, convex hull reduced to four corners.
 * 5. Discard regions touching three or more image edges (the table around the card).
 * 6. Keep quads that are convex, card-shaped (aspect ≈1.25-2.1 either way), fill their hull and are mostly
 *    solid; the best scoring one wins.
 *
 * Returns null when nothing is reliable: the caller then crops by the capture guide and offers manual cropping.
 */
class CardDetector(private val workSize: Int = 640) {
    data class Detection(val quad: Quad, val score: Double)

    /** Detects in [image]; the result is in [image]'s pixel coordinates. */
    fun detect(image: LumaImage): Detection? {
        val small = image.downscale(workSize).blur()
        val threshold = otsu(small)
        val candidates = listOf(true, false).mapNotNull { brightCard ->
            val mask = BooleanArray(small.data.size) { (small.data[it] > threshold) == brightCard }
            bestQuad(close(open(mask, small.width, small.height, 1), small.width, small.height, 3), small.width, small.height)
        }
        val best = candidates.maxByOrNull { it.score } ?: return null
        val sx = image.width.toDouble() / small.width
        val sy = image.height.toDouble() / small.height
        return Detection(best.quad.scaled(sx, sy), best.score)
    }

    private fun bestQuad(mask: BooleanArray, w: Int, h: Int): Detection? {
        val labels = IntArray(mask.size)
        val sizes = mutableListOf(0) // index 0 = background
        val stack = IntArray(mask.size)
        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue
            val label = sizes.size
            var count = 0
            var top = 0
            fun visit(j: Int) {
                if (mask[j] && labels[j] == 0) {
                    labels[j] = label
                    stack[top++] = j
                }
            }
            visit(start)
            while (top > 0) {
                val i = stack[--top]
                count++
                val x = i % w
                val y = i / w
                if (x > 0) visit(i - 1)
                if (x < w - 1) visit(i + 1)
                if (y > 0) visit(i - w)
                if (y < h - 1) visit(i + w)
            }
            sizes += count
        }
        val total = (w * h).toDouble()
        return sizes.indices.drop(1)
            .filter { sizes[it] >= MIN_AREA * total && sizes[it] <= MAX_AREA * total }
            .mapNotNull { evaluate(it, sizes[it], labels, w, h, total) }
            .maxByOrNull { it.score }
    }

    private fun evaluate(label: Int, pixels: Int, labels: IntArray, w: Int, h: Int, total: Double): Detection? {
        // Row extremes are enough for the hull of a region.
        val boundary = ArrayList<Point>()
        var touchesTop = false
        var touchesBottom = false
        var touchesLeft = false
        var touchesRight = false
        for (y in 0 until h) {
            var minX = -1
            var maxX = -1
            val row = y * w
            for (x in 0 until w) {
                if (labels[row + x] == label) {
                    if (minX < 0) minX = x
                    maxX = x
                }
            }
            if (minX >= 0) {
                touchesTop = touchesTop || y == 0
                touchesBottom = touchesBottom || y == h - 1
                touchesLeft = touchesLeft || minX == 0
                touchesRight = touchesRight || maxX == w - 1
                boundary += Point(minX.toDouble(), y.toDouble())
                boundary += Point(maxX + 1.0, y.toDouble())
                boundary += Point(minX.toDouble(), y + 1.0)
                boundary += Point(maxX + 1.0, y + 1.0)
            }
        }
        // A region touching three or four edges is the table around the card (or a card filling the whole
        // photo, where the guide crop is as good): never the card to straighten.
        if (listOf(touchesTop, touchesBottom, touchesLeft, touchesRight).count { it } > 2) return null
        val hull = Geometry.convexHull(boundary)
        if (hull.size < 4) return null
        val quad = Quad.ordered(Geometry.simplify(hull, 4))
        if (!Geometry.isConvex(quad)) return null
        val hullArea = Geometry.polygonArea(hull)
        val quadArea = quad.area
        val coverage = quadArea / hullArea // the four corners describe the region's outline
        val solidity = pixels / hullArea // the region is one piece, not a scattered pattern
        val aspect = aspectRatio(quad)
        if (coverage < MIN_COVERAGE || solidity < MIN_SOLIDITY || aspect !in MIN_ASPECT..MAX_ASPECT) return null
        val areaFraction = quadArea / total
        return Detection(quad, coverage * solidity * (0.5 + areaFraction))
    }

    companion object {
        private const val MIN_AREA = 0.06
        private const val MAX_AREA = 0.97
        private const val MIN_COVERAGE = 0.92
        private const val MIN_SOLIDITY = 0.72
        private const val MIN_ASPECT = 1.25
        private const val MAX_ASPECT = 2.1

        /** Long side over short side, averaging opposite edges (perspective makes them differ). */
        internal fun aspectRatio(quad: Quad): Double {
            val horizontal = (quad.topLeft.distanceTo(quad.topRight) + quad.bottomLeft.distanceTo(quad.bottomRight)) / 2
            val vertical = (quad.topLeft.distanceTo(quad.bottomLeft) + quad.topRight.distanceTo(quad.bottomRight)) / 2
            return max(horizontal, vertical) / max(1e-9, min(horizontal, vertical))
        }

        /** Otsu's threshold over the 256-bin histogram. */
        internal fun otsu(image: LumaImage): Int {
            val histogram = IntArray(256)
            image.data.forEach { histogram[it.coerceIn(0, 255)]++ }
            val total = image.data.size.toDouble()
            val sumAll = histogram.indices.sumOf { it.toDouble() * histogram[it] }
            var sumBackground = 0.0
            var weightBackground = 0.0
            var best = 0.0
            var threshold = 127
            for (t in 0 until 256) {
                weightBackground += histogram[t]
                if (weightBackground == 0.0) continue
                val weightForeground = total - weightBackground
                if (weightForeground == 0.0) break
                sumBackground += t.toDouble() * histogram[t]
                val meanB = sumBackground / weightBackground
                val meanF = (sumAll - sumBackground) / weightForeground
                val between = weightBackground * weightForeground * (meanB - meanF) * (meanB - meanF)
                if (between > best) {
                    best = between
                    threshold = t
                }
            }
            return threshold
        }

        private fun open(mask: BooleanArray, w: Int, h: Int, radius: Int) = dilate(erode(mask, w, h, radius), w, h, radius)

        private fun close(mask: BooleanArray, w: Int, h: Int, radius: Int) = erode(dilate(mask, w, h, radius), w, h, radius)

        private fun erode(mask: BooleanArray, w: Int, h: Int, radius: Int) = morph(mask, w, h, radius, all = true)

        private fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int) = morph(mask, w, h, radius, all = false)

        /** Square structuring element, separable: rows then columns. */
        private fun morph(mask: BooleanArray, w: Int, h: Int, radius: Int, all: Boolean): BooleanArray {
            val rows = BooleanArray(mask.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var v = all
                    for (k in max(0, x - radius)..min(w - 1, x + radius)) {
                        val m = mask[y * w + k]
                        if (all && !m) {
                            v = false
                            break
                        }
                        if (!all && m) {
                            v = true
                            break
                        }
                    }
                    rows[y * w + x] = v
                }
            }
            val out = BooleanArray(mask.size)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var v = all
                    for (k in max(0, y - radius)..min(h - 1, y + radius)) {
                        val m = rows[k * w + x]
                        if (all && !m) {
                            v = false
                            break
                        }
                        if (!all && m) {
                            v = true
                            break
                        }
                    }
                    out[y * w + x] = v
                }
            }
            return out
        }
    }
}

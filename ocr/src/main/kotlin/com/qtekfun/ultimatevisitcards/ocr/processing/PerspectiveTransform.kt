package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Straightens the region inside a [Quad] into an upright rectangle (homography + bilinear sampling). */
object PerspectiveTransform {
    /**
     * Output size: the longest of each pair of opposite edges, scaled down so the long side is at most
     * [maxLongSide] (never scaled up).
     */
    fun warp(source: ArgbImage, quad: Quad, maxLongSide: Int): ArgbImage {
        val width = max(quad.topLeft.distanceTo(quad.topRight), quad.bottomLeft.distanceTo(quad.bottomRight))
        val height = max(quad.topLeft.distanceTo(quad.bottomLeft), quad.topRight.distanceTo(quad.bottomRight))
        val scale = minOf(1.0, maxLongSide / max(width, height))
        val outW = max(1, (width * scale).roundToInt())
        val outH = max(1, (height * scale).roundToInt())
        // Maps output pixel centres to source coordinates.
        val h = homography(
            listOf(Point(0.0, 0.0), Point(outW.toDouble(), 0.0), Point(outW.toDouble(), outH.toDouble()), Point(0.0, outH.toDouble())),
            quad.points
        )
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val u = x + 0.5
                val v = y + 0.5
                val d = h[6] * u + h[7] * v + 1.0
                val sx = (h[0] * u + h[1] * v + h[2]) / d
                val sy = (h[3] * u + h[4] * v + h[5]) / d
                out[y * outW + x] = sample(source, sx - 0.5, sy - 0.5)
            }
        }
        return ArgbImage(outW, outH, out)
    }

    /** Axis-aligned crop (used when no card was detected: crop by the capture guide). */
    fun crop(source: ArgbImage, left: Int, top: Int, right: Int, bottom: Int, maxLongSide: Int): ArgbImage {
        val l = left.coerceIn(0, source.width - 1)
        val t = top.coerceIn(0, source.height - 1)
        val r = right.coerceIn(l + 1, source.width)
        val b = bottom.coerceIn(t + 1, source.height)
        val quad = Quad(
            Point(l.toDouble(), t.toDouble()),
            Point(r.toDouble(), t.toDouble()),
            Point(r.toDouble(), b.toDouble()),
            Point(l.toDouble(), b.toDouble())
        )
        return warp(source, quad, maxLongSide)
    }

    private fun sample(image: ArgbImage, x: Double, y: Double): Int {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val p00 = pixel(image, x0, y0)
        val p10 = pixel(image, x0 + 1, y0)
        val p01 = pixel(image, x0, y0 + 1)
        val p11 = pixel(image, x0 + 1, y0 + 1)
        var result = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val c = ((p00 shr shift) and 0xFF) * (1 - fx) * (1 - fy) +
                ((p10 shr shift) and 0xFF) * fx * (1 - fy) +
                ((p01 shr shift) and 0xFF) * (1 - fx) * fy +
                ((p11 shr shift) and 0xFF) * fx * fy
            result = result or (c.roundToInt().coerceIn(0, 255) shl shift)
        }
        return result
    }

    private fun pixel(image: ArgbImage, x: Int, y: Int): Int = image.pixels[y.coerceIn(0, image.height - 1) * image.width + x.coerceIn(0, image.width - 1)]

    /** The 3×3 homography (h22 = 1, row-major, 8 values) mapping [from] onto [to]. */
    internal fun homography(from: List<Point>, to: List<Point>): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]
            val (u, v) = to[i]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting.
        for (col in 0 until 8) {
            val pivot = (col until 8).maxBy { abs(a[it][col]) }
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp
            require(abs(a[col][col]) > 1e-12) { "Degenerate quad" }
            for (row in 0 until 8) {
                if (row == col) continue
                val f = a[row][col] / a[col][col]
                for (k in col..8) a[row][k] -= f * a[col][k]
            }
        }
        return DoubleArray(8) { a[it][8] / a[it][it] }
    }
}

package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Grey-level image (0-255 per pixel), row-major. The processing code works on these plain arrays instead
 * of `Bitmap`, so it is pure Kotlin and testable on the JVM; `Bitmaps.kt` converts at the edges.
 */
class LumaImage(val width: Int, val height: Int, val data: IntArray) {
    init {
        require(width > 0 && height > 0 && data.size == width * height) { "Bad luma image ${width}x$height" }
    }

    operator fun get(x: Int, y: Int): Int = data[y * width + x]

    /** Area-averaged downscale so the long side is at most [maxSide]; returns this image if already small. */
    fun downscale(maxSide: Int): LumaImage {
        val factor = max(width, height).toDouble() / maxSide
        if (factor <= 1.0) return this
        val w = max(1, (width / factor).roundToInt())
        val h = max(1, (height / factor).roundToInt())
        val out = IntArray(w * h)
        for (oy in 0 until h) {
            val y0 = oy * height / h
            val y1 = max(y0 + 1, (oy + 1) * height / h)
            for (ox in 0 until w) {
                val x0 = ox * width / w
                val x1 = max(x0 + 1, (ox + 1) * width / w)
                var sum = 0L
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) sum += data[row + x]
                }
                out[oy * w + ox] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return LumaImage(w, h, out)
    }

    /** 5×5 Gaussian blur (separable binomial kernel 1-4-6-4-1), clamping at the borders. */
    fun blur(): LumaImage {
        val kernel = intArrayOf(1, 4, 6, 4, 1)
        val tmp = IntArray(data.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0
                for (k in -2..2) sum += kernel[k + 2] * data[row + (x + k).coerceIn(0, width - 1)]
                tmp[row + x] = sum
            }
        }
        val out = IntArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                for (k in -2..2) sum += kernel[k + 2] * tmp[(y + k).coerceIn(0, height - 1) * width + x]
                out[y * width + x] = sum / 256
            }
        }
        return LumaImage(width, height, out)
    }

    companion object {
        /** ITU-R BT.601 luma of ARGB pixels. */
        fun fromArgb(image: ArgbImage): LumaImage = LumaImage(
            image.width,
            image.height,
            IntArray(image.pixels.size) { i ->
                val p = image.pixels[i]
                (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            }
        )
    }
}

/** Colour image as ARGB_8888 ints, row-major (the layout of `Bitmap.getPixels`). */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Bad image ${width}x$height" }
    }
}

/** A point in pixel coordinates. */
data class Point(val x: Double, val y: Double) {
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)

    fun distanceTo(o: Point): Double = kotlin.math.hypot(x - o.x, y - o.y)
}

/** Four corners in pixel coordinates, clockwise from the top-left one. */
data class Quad(val topLeft: Point, val topRight: Point, val bottomRight: Point, val bottomLeft: Point) {
    val points: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Shoelace area. */
    val area: Double get() = Geometry.polygonArea(points)

    fun scaled(sx: Double, sy: Double) = Quad(
        Point(topLeft.x * sx, topLeft.y * sy),
        Point(topRight.x * sx, topRight.y * sy),
        Point(bottomRight.x * sx, bottomRight.y * sy),
        Point(bottomLeft.x * sx, bottomLeft.y * sy)
    )

    companion object {
        /**
         * Orders four arbitrary corners clockwise (image y axis points down). The first corner is chosen so
         * the top edge is the one closest to horizontal and above the centre: the card keeps the orientation
         * it has in the photo, whether landscape or portrait.
         */
        fun ordered(corners: List<Point>): Quad {
            require(corners.size == 4) { "A quad has four corners" }
            val cx = corners.sumOf { it.x } / 4
            val cy = corners.sumOf { it.y } / 4
            val clockwise = corners.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
            val start = clockwise.indices.minBy { i ->
                val a = clockwise[i]
                val b = clockwise[(i + 1) % 4]
                // Edge pointing rightwards across the top: small angle to the x axis, midpoint above the centre.
                val angle = kotlin.math.abs(kotlin.math.atan2(b.y - a.y, b.x - a.x))
                angle + if ((a.y + b.y) / 2 < cy) 0.0 else kotlin.math.PI
            }
            val o = List(4) { clockwise[(start + it) % 4] }
            return Quad(o[0], o[1], o[2], o[3])
        }
    }
}

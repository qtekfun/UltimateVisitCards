package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class PerspectiveTransformTest {
    @Test
    fun `the homography maps the four corners`() {
        val from = listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 50.0), Point(0.0, 50.0))
        val to = listOf(Point(10.0, 20.0), Point(210.0, 40.0), Point(190.0, 150.0), Point(5.0, 120.0))
        val h = PerspectiveTransform.homography(from, to)
        from.zip(to).forEach { (p, q) ->
            val d = h[6] * p.x + h[7] * p.y + 1
            assertTrue(abs((h[0] * p.x + h[1] * p.y + h[2]) / d - q.x) < 1e-6)
            assertTrue(abs((h[3] * p.x + h[4] * p.y + h[5]) / d - q.y) < 1e-6)
        }
    }

    @Test
    fun `warping a rotated card straightens it and keeps the card proportions`() {
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 425.0, 30.0)
        val photo = SyntheticImages.argbFromLuma(SyntheticImages.photo(800, 600, quad, noise = 0))
        val card = PerspectiveTransform.warp(photo, quad, maxLongSide = 1600)
        assertEquals(425, card.width, "width")
        assertEquals(275, card.height, "height")
        val luma = LumaImage.fromArgb(card)
        // Just inside every edge there is card (no text there), never table: the crop is aligned.
        val edges = (4 until card.width - 4).flatMap { x -> listOf(luma[x, 4], luma[x, card.height - 5]) } +
            (4 until card.height - 4).flatMap { y -> listOf(luma[4, y], luma[card.width - 5, y]) }
        assertTrue(edges.all { it > 200 }, "Table pixels at the edges: ${edges.filter { it <= 200 }.take(5)}")
        // The text lines survive, in the left part of the card.
        assertTrue((0 until card.height).any { y -> luma[card.width / 6, y] < 60 })
    }

    @Test
    fun `output is scaled down to the maximum size, never up`() {
        val image = ArgbImage(400, 200, IntArray(400 * 200) { 0xFF336699.toInt() })
        val quad = Quad(Point(0.0, 0.0), Point(400.0, 0.0), Point(400.0, 200.0), Point(0.0, 200.0))
        val small = PerspectiveTransform.warp(image, quad, maxLongSide = 100)
        assertEquals(100 to 50, small.width to small.height)
        val same = PerspectiveTransform.warp(image, quad, maxLongSide = 1600)
        assertEquals(400 to 200, same.width to same.height)
        assertEquals(0xFF336699.toInt(), same.pixels[200 * 100 + 50])
    }

    @Test
    fun `cropping clamps to the image`() {
        val image = ArgbImage(100, 80, IntArray(100 * 80) { 0xFFFFFFFF.toInt() })
        val crop = PerspectiveTransform.crop(image, -10, 10, 200, 70, 1600)
        assertEquals(100 to 60, crop.width to crop.height)
    }
}

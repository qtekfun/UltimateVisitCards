package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.math.hypot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CardDetectorTest {
    private val detector = CardDetector()

    /** Every detected corner is within [tolerance] of the image diagonal from the true one. */
    private fun assertCorners(expected: Quad, actual: Quad?, width: Int, height: Int, tolerance: Double = 0.025) {
        assertNotNull(actual, "No card detected")
        val diagonal = hypot(width.toDouble(), height.toDouble())
        expected.points.zip(actual.points).forEachIndexed { i, (e, a) ->
            assertTrue(e.distanceTo(a) < tolerance * diagonal, "Corner $i: expected $e, got $a")
        }
    }

    @Test
    fun `finds a straight card`() {
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 560.0, 0.0)
        assertCorners(quad, detector.detect(SyntheticImages.photo(800, 600, quad))?.quad, 800, 600)
    }

    @Test
    fun `finds rotated cards`() {
        for (angle in listOf(-40.0, -12.0, 8.0, 25.0, 40.0)) {
            val quad = SyntheticImages.rotatedCard(400.0, 300.0, 420.0, angle)
            assertCorners(quad, detector.detect(SyntheticImages.photo(800, 600, quad, seed = angle.toInt()))?.quad, 800, 600)
        }
    }

    @Test
    fun `at 45 degrees any corner can be the first one, but all four are found`() {
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 420.0, 45.0)
        val found = assertNotNull(detector.detect(SyntheticImages.photo(800, 600, quad))).quad
        val diagonal = hypot(800.0, 600.0)
        quad.points.forEach { e -> assertTrue(found.points.any { it.distanceTo(e) < 0.025 * diagonal }, "Missing corner $e") }
    }

    @Test
    fun `finds a vertical card in a portrait photo`() {
        val quad = SyntheticImages.rotatedCard(300.0, 400.0, 480.0, 90.0)
        assertCorners(quad, detector.detect(SyntheticImages.photo(600, 800, quad))?.quad, 600, 800)
    }

    @Test
    fun `finds a card seen in perspective`() {
        val quad = Quad(Point(170.0, 140.0), Point(640.0, 170.0), Point(700.0, 470.0), Point(110.0, 450.0))
        assertCorners(quad, detector.detect(SyntheticImages.photo(800, 600, quad))?.quad, 800, 600)
    }

    @Test
    fun `finds a dark card on a light table`() {
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 500.0, 15.0)
        val photo = SyntheticImages.photo(800, 600, quad, card = 45, background = 205, text = 230)
        assertCorners(quad, detector.detect(photo)?.quad, 800, 600)
    }

    @Test
    fun `works on full-resolution photos and returns their coordinates`() {
        val quad = SyntheticImages.rotatedCard(1200.0, 900.0, 1500.0, -10.0)
        assertCorners(quad, detector.detect(SyntheticImages.photo(2400, 1800, quad))?.quad, 2400, 1800)
    }

    @Test
    fun `nothing card-like gives null`() {
        assertNull(detector.detect(SyntheticImages.photo(800, 600, null)))
    }

    @Test
    fun `a tiny card is not trusted`() {
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 120.0, 0.0)
        assertNull(detector.detect(SyntheticImages.photo(800, 600, quad)))
    }

    @Test
    fun `a square is not a card`() {
        val square = Quad(Point(250.0, 150.0), Point(550.0, 150.0), Point(550.0, 450.0), Point(250.0, 450.0))
        assertNull(detector.detect(SyntheticImages.photo(800, 600, square)))
    }
}

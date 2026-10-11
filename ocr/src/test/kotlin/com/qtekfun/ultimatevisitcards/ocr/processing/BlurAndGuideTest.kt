package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BlurAndGuideTest {
    @Test
    fun `a sharp card is sharp and the same card blurred is not`() {
        val quad = SyntheticImages.rotatedCard(320.0, 240.0, 420.0, 5.0)
        val sharp = SyntheticImages.photo(640, 480, quad, noise = 2)
        var blurred = sharp
        repeat(6) { blurred = blurred.blur() }
        assertFalse(BlurMeter.isBlurry(sharp), "sharpness ${BlurMeter.sharpness(sharp)}")
        assertTrue(BlurMeter.isBlurry(blurred), "sharpness ${BlurMeter.sharpness(blurred)}")
    }

    @Test
    fun `the guide is a centred card-shaped rectangle in portrait and landscape`() {
        val portrait = CaptureGuide.rectFor(1800, 2400)
        assertEquals(108, portrait.left)
        assertEquals(1800 - 108, portrait.right)
        val ratio = (portrait.right - portrait.left).toDouble() / (portrait.bottom - portrait.top)
        assertTrue(ratio in 1.53..1.56)
        // Centred, give or take the rounding of one pixel.
        assertTrue(kotlin.math.abs((2400 - portrait.bottom) - portrait.top) <= 1)

        val landscape = CaptureGuide.rectFor(2400, 1000)
        assertEquals(880, landscape.bottom - landscape.top)
        assertTrue(landscape.left > 0 && landscape.right < 2400)
    }

    @Test
    fun `auto capture fires once the card stays still and sharp`() {
        val tracker = StabilityTracker(holdMillis = 700)
        val quad = SyntheticImages.rotatedCard(400.0, 300.0, 400.0, 0.0)
        val nudged = quad.scaled(1.005, 1.0)
        val diagonal = 1000.0
        assertFalse(tracker.onFrame(quad, sharp = true, diagonal, 0))
        assertFalse(tracker.onFrame(nudged, sharp = true, diagonal, 400))
        assertTrue(tracker.onFrame(nudged, sharp = true, diagonal, 750))
        // Fires once per stable period.
        assertFalse(tracker.onFrame(nudged, sharp = true, diagonal, 1500))
        // A blurry frame or a moved card restarts the wait.
        assertFalse(tracker.onFrame(nudged, sharp = false, diagonal, 1600))
        val moved = SyntheticImages.rotatedCard(450.0, 300.0, 400.0, 0.0)
        assertFalse(tracker.onFrame(moved, sharp = true, diagonal, 1700))
        assertFalse(tracker.onFrame(moved, sharp = true, diagonal, 2000))
        assertTrue(tracker.onFrame(moved, sharp = true, diagonal, 2500))
    }
}

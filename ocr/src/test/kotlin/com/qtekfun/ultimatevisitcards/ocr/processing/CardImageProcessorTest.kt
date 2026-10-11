package com.qtekfun.ultimatevisitcards.ocr.processing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.NormPoint
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardImageProcessorTest {
    private val processor = CardImageProcessor()

    @Test
    fun `a detected card is straightened and its normalised corners kept`() {
        val quad = SyntheticImages.rotatedCard(800.0, 600.0, 1100.0, 12.0)
        val photo = Bitmaps.toBitmap(SyntheticImages.argbFromLuma(SyntheticImages.photo(1600, 1200, quad)))
        val result = processor.process(photo)
        assertTrue(result.detected)
        val ratio = result.card.width.toDouble() / result.card.height
        assertTrue(ratio in 1.50..1.60, "ratio $ratio")
        assertEquals(quad.topLeft.x / 1600, result.quad.topLeft.x.toDouble(), 0.02)
        assertEquals(quad.topLeft.y / 1200, result.quad.topLeft.y.toDouble(), 0.02)
    }

    @Test
    fun `without a card the guide is the crop and manual cropping is suggested`() {
        val photo = Bitmaps.toBitmap(SyntheticImages.argbFromLuma(SyntheticImages.photo(1200, 1600, null)))
        val result = processor.process(photo)
        assertFalse(result.detected)
        val guide = CaptureGuide.rectFor(1200, 1600)
        assertEquals(guide.right - guide.left, result.card.width)
        assertEquals(guide.bottom - guide.top, result.card.height)
        assertEquals(guide.left / 1200f, result.quad.topLeft.x, 0.001f)
    }

    @Test
    fun `manual corners re-crop the original, in any order`() {
        val photo = Bitmaps.toBitmap(SyntheticImages.argbFromLuma(SyntheticImages.photo(1000, 800, null)))
        val quad = CardQuad(NormPoint(0.8f, 0.75f), NormPoint(0.1f, 0.75f), NormPoint(0.1f, 0.25f), NormPoint(0.8f, 0.25f))
        val card = processor.crop(photo, quad)
        assertEquals(700 to 400, card.width to card.height)
    }

    @Test
    fun `analysis frames are rotated upright`() {
        // 3×2 image, values 0..5 row by row.
        val image = LumaImage(3, 2, intArrayOf(0, 1, 2, 3, 4, 5))
        val r90 = Bitmaps.rotate(image, 90)
        assertEquals(2 to 3, r90.width to r90.height)
        assertEquals(listOf(3, 0, 4, 1, 5, 2), r90.data.toList())
        assertEquals(listOf(5, 4, 3, 2, 1, 0), Bitmaps.rotate(image, 180).data.toList())
        assertEquals(listOf(2, 5, 1, 4, 0, 3), Bitmaps.rotate(image, 270).data.toList())
    }
}

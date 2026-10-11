package com.qtekfun.ultimatevisitcards.ocr.processing

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ImagePreprocessorTest {
    @Test
    fun `the OCR input is rescaled to the target size, up or down`() {
        val small = SyntheticImages.argbFromLuma(LumaImage(600, 388, IntArray(600 * 388) { 200 }))
        assertEquals(1200, ImagePreprocessor.forTesseract(small).width)
        val big = SyntheticImages.argbFromLuma(LumaImage(1600, 1035, IntArray(1600 * 1035) { 200 }))
        assertEquals(1200, ImagePreprocessor.forTesseract(big).width)
    }

    @Test
    fun `text survives uneven lighting and the paper turns white`() {
        // A card lit from the left: paper goes from 230 to 110, text is 60 darker than the paper around it.
        val w = 1200
        val h = 776
        val image = LumaImage(
            w,
            h,
            IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                val paper = 230 - 120 * x / w
                val text = y in 300..330 && (x / 20) % 2 == 0
                if (text) paper - 60 else paper
            }
        )
        val binary = ImagePreprocessor.forTesseract(SyntheticImages.argbFromLuma(image))
        assertTrue(binary.data.all { it == 0 || it == 255 })
        // Ink found on both the bright and the dark side.
        assertEquals(0, binary[10, 315])
        assertEquals(0, binary[1170, 315])
        // Paper stays paper on both sides.
        assertEquals(255, binary[10, 100])
        assertEquals(255, binary[1170, 100])
    }
}

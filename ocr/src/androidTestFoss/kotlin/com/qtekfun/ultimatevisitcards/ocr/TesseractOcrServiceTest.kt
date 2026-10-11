package com.qtekfun.ultimatevisitcards.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.ocr.foss.TesseractOcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test on a device: the bundled Tesseract models are installed and read a generated image. */
@RunWith(AndroidJUnit4::class)
class TesseractOcrServiceTest {
    @Test
    fun readsKnownText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = TesseractOcrService(context, Dispatchers.IO)
        val result = service.recognize(OcrImage(TextImage.write(context.cacheDir)))
        service.release()
        assertEquals("tesseract", service.engineId)
        assertTrue(result.text, result.text.contains("ULTIMATE"))
        assertTrue(result.text, result.text.contains("Ana Ruiz"))
        assertTrue(result.lines.all { it.confidence != null })
    }
}

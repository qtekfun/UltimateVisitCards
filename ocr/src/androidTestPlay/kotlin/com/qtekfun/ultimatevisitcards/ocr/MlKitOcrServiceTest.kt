package com.qtekfun.ultimatevisitcards.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.ocr.play.MlKitOcrService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test on a device: the bundled ML Kit model reads a generated image offline. */
@RunWith(AndroidJUnit4::class)
class MlKitOcrServiceTest {
    @Test
    fun readsKnownText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MlKitOcrService()
        val result = service.recognize(OcrImage(TextImage.write(context.cacheDir)))
        service.release()
        assertEquals("mlkit", service.engineId)
        assertTrue(result.text, result.text.contains("ULTIMATE"))
        assertTrue(result.text, result.text.contains("Ana Ruiz"))
        assertTrue(result.lines.all { it.box.left in 0f..1f && it.box.bottom in 0f..1f })
    }
}

package com.qtekfun.ultimatevisitcards.ocr.di

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrEngineInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrModuleTest {
    @Test
    fun `play binds ML Kit`() {
        assertEquals(OcrEngineInfo.TESSERACT, OcrModule.provideOcrEngineInfo().engineId)
    }
}

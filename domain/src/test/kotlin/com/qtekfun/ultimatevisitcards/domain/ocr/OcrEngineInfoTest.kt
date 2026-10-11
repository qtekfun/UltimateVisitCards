package com.qtekfun.ultimatevisitcards.domain.ocr

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class OcrEngineInfoTest {
    @Test
    fun `keeps the engine id`() {
        assertEquals("mlkit", OcrEngineInfo(OcrEngineInfo.MLKIT).engineId)
    }

    @Test
    fun `rejects a blank engine id`() {
        assertFailsWith<IllegalArgumentException> { OcrEngineInfo(" ") }
    }
}

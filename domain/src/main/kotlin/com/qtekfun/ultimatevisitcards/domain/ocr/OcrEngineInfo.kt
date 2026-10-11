package com.qtekfun.ultimatevisitcards.domain.ocr

/**
 * Identifies the OCR engine bound for the current flavour (`mlkit` in `play`, `tesseract` in `foss`).
 *
 * Phase 0 placeholder: in phase 3 the engine id moves to `OcrService.engineId` (see docs/04-ocr-extraccion.md).
 */
data class OcrEngineInfo(val engineId: String) {
    init {
        require(engineId.isNotBlank()) { "engineId must not be blank" }
    }

    companion object {
        const val MLKIT = "mlkit"
        const val TESSERACT = "tesseract"
    }
}

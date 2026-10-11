package com.qtekfun.ultimatevisitcards.ocr.di

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.ocr.foss.TesseractOcrService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** `foss` flavour: Tesseract4Android with bundled traineddata. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    abstract fun ocrService(impl: TesseractOcrService): OcrService
}

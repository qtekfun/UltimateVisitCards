package com.qtekfun.ultimatevisitcards.ocr.di

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrEngineInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** `foss` flavour: Tesseract4Android with bundled traineddata (wired in phase 3). */
@Module
@InstallIn(SingletonComponent::class)
object OcrModule {
    @Provides
    fun provideOcrEngineInfo(): OcrEngineInfo = OcrEngineInfo(OcrEngineInfo.TESSERACT)
}

package com.qtekfun.ultimatevisitcards.ocr.di

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.ocr.play.MlKitOcrService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** `play` flavour: ML Kit with the bundled Latin model. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    abstract fun ocrService(impl: MlKitOcrService): OcrService
}

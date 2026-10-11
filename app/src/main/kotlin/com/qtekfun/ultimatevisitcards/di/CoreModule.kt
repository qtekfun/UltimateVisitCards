package com.qtekfun.ultimatevisitcards.di

import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Time and ids, injected so every layer stays deterministic under test. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun idGenerator(): IdGenerator = IdGenerator.Uuid
}

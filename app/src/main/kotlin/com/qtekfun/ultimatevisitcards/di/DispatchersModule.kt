package com.qtekfun.ultimatevisitcards.di

import com.qtekfun.ultimatevisitcards.domain.util.DefaultDispatcher
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Dispatchers are always injected: no hard-coded `Dispatchers.IO` in the logic (see CLAUDE.md). */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

package com.qtekfun.ultimatevisitcards.di

import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.DefaultDispatcher
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Scope that outlives screens, for work that must finish after the user leaves (e.g. clean-ups). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Time and ids, injected so every layer stays deterministic under test. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun idGenerator(): IdGenerator = IdGenerator.Uuid

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

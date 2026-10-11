package com.qtekfun.ultimatevisitcards.data.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatevisitcards.data.R
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.SectorSeedCallback
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomEventRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardRepository
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.EventRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Room, the search indexer and the repositories (docs/01-arquitectura.md). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun sectorRepository(impl: RoomSectorRepository): SectorRepository

    @Binds
    abstract fun companyRepository(impl: RoomCompanyRepository): CompanyRepository

    @Binds
    abstract fun contactRepository(impl: RoomContactRepository): ContactRepository

    @Binds
    abstract fun eventRepository(impl: RoomEventRepository): EventRepository

    @Binds
    abstract fun businessCardRepository(impl: RoomBusinessCardRepository): BusinessCardRepository

    companion object {
        // The spread copies a small array once, when the database is built.
        @Suppress("SpreadOperator")
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher, clock: Clock, ids: IdGenerator): AppDatabase =
            Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(io)
                .addMigrations(*AppDatabase.MIGRATIONS)
                .addCallback(
                    SectorSeedCallback(
                        names = { context.resources.getStringArray(R.array.default_sectors).toList() },
                        clock = clock,
                        ids = ids
                    )
                )
                .build()

        @Provides
        @Singleton
        fun searchIndexer(db: AppDatabase): SearchIndexer = SearchIndexer(db)

        @Provides
        @Singleton
        fun companyNameNormalizer(): CompanyNameNormalizer = CompanyNameNormalizer.fromBundledDictionary()
    }
}

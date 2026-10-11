package com.qtekfun.ultimatevisitcards.testing

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomEventRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import kotlinx.coroutines.CoroutineDispatcher

/** In-memory database with the repositories of the catalogue screens (events, companies, sectors, contacts). */
class CatalogTestData(dispatcher: CoroutineDispatcher) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(dispatcher)
        .build()

    /** Ticks one second per read, so «newest first» orders are deterministic. */
    private var now = 1_767_225_600_000L
    private val clock = Clock { now.also { now += 1_000 } }
    private val indexer = SearchIndexer(db)
    val events = RoomEventRepository(db, indexer, clock, IdGenerator.Uuid)
    val companies = RoomCompanyRepository(db, indexer, CompanyNameNormalizer.fromBundledDictionary(), clock, IdGenerator.Uuid)
    val contacts = RoomContactRepository(db, indexer, clock, IdGenerator.Uuid)
    val sectors = RoomSectorRepository(db, indexer, clock, IdGenerator.Uuid)

    fun close() = db.close()
}

/** The value of a result the test expects to succeed. */
fun <T> DomainResult<T>.orFail(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> throw AssertionError("Expected success, got $error")
}

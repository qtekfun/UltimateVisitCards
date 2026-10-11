package com.qtekfun.ultimatevisitcards.data.testing

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomEventRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import kotlin.coroutines.CoroutineContext

/** A clock the test moves by hand; starts at 2026-01-01 UTC and ticks one second per read. */
class FakeClock(var now: Long = 1_767_225_600_000L) : Clock {
    override fun nowMillis(): Long = now.also { now += 1_000 }
}

/**
 * An in-memory database with the real schema (bundled SQLite, so FTS4 behaves as on a device) and all the
 * repositories wired as in production. Synthetic data only.
 */
class TestData(context: CoroutineContext, callback: RoomDatabase.Callback? = null) {
    val clock = FakeClock()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(context)
        .apply { callback?.let { addCallback(it) } }
        .build()

    private val ids = IdGenerator.Uuid
    val indexer = SearchIndexer(db)
    val sectors = RoomSectorRepository(db, indexer, clock, ids)
    val companies = RoomCompanyRepository(db, indexer, CompanyNameNormalizer.fromBundledDictionary(), clock, ids)
    val contacts = RoomContactRepository(db, indexer, clock, ids)
    val events = RoomEventRepository(db, indexer, clock, ids)
    val cards = RoomBusinessCardRepository(db, indexer, clock, ids)

    fun close() = db.close()
}

/** The value of a result the test expects to succeed. */
fun <T> DomainResult<T>.value(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> throw AssertionError("Expected success, got $error")
}

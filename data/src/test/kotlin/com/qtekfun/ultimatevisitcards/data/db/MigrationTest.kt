package com.qtekfun.ultimatevisitcards.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexHealth
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Schema versions against the exported JSON in `data/schemas`. Every future migration adds its case here
 * (`createDatabase(n)` → `runMigrationsAndValidate(n + 1, listOf(MigrationNToN1))`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun helper(file: File) = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class
    )

    @Test
    fun `1 to 2 recreates the search index and keeps the data, then the index is rebuilt`() = runTest {
        val file = File(folder.root, "test.db")
        helper(file).createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO Company (id, name, normalizedName, sectorId, country, website, notes, createdAt, updatedAt) " +
                    "VALUES ('co', 'ABC Marine', 'abc marine', NULL, 'Spain', NULL, NULL, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO Contact (id, firstName, lastName, jobTitle, companyId, country, phone, mobile, email, website, address, notes, " +
                    "exportedToPhoneAt, createdAt, updatedAt) " +
                    "VALUES ('c1', 'José', 'Muñoz', 'Director', 'co', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1)"
            )
            db.execSQL(
                "INSERT INTO contact_fts (contactId, name, company, jobTitle, country, sector, email, phone, notes, ocrText, events) VALUES ('c1', 'jose munoz', 'abc marine', 'director', 'spain', '', '', '', '', '', '')"
            )
        }
        helper(file).runMigrationsAndValidate(2, listOf(Migration1To2)).use { db ->
            db.prepare("SELECT COUNT(*) FROM Contact").use {
                it.step()
                assertEquals(1, it.getLong(0))
            }
            db.prepare("SELECT COUNT(*) FROM contact_fts").use {
                it.step()
                assertEquals(0, it.getLong(0))
            }
        }
        // First start after the update: the index is rebuilt and the name filters work.
        val database = Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>(), file.path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            assertTrue(SearchIndexHealth(database, SearchIndexer(database)).ensure())
            assertFalse(SearchIndexHealth(database, SearchIndexer(database)).ensure())
            val found = database.contactDao().observeList(ContactQueryBuilder.build(ContactFilter(lastName = "munoz"))).first()
            assertEquals(listOf("c1"), found.map { it.contact.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun `the newest version matches the exported schema`() = runTest {
        val file = File(folder.root, "test.db")
        helper(file).createDatabase(1).close()
        val connection = helper(file).runMigrationsAndValidate(AppDatabase.VERSION, AppDatabase.MIGRATIONS.toList())
        connection.prepare("SELECT name FROM sqlite_master WHERE name = 'contact_fts'").use {
            it.step()
            assertEquals("contact_fts", it.getText(0))
        }
        connection.close()
    }

    @Test
    fun `there is a migration for every version step`() {
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until AppDatabase.VERSION).map { it to it + 1 }, steps)
    }
}

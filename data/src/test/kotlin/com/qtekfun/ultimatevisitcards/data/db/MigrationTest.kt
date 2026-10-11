package com.qtekfun.ultimatevisitcards.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.assertEquals
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
    fun `version 1 matches the exported schema`() = runTest {
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

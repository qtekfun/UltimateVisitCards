package com.qtekfun.ultimatevisitcards.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator

/**
 * Seeds the initial, editable sector list when the database is created (first launch), in the device
 * language at that moment. It runs only once: sectors the user deletes are not seeded again.
 *
 * The names come from the `default_sectors` string array, never from code (see CONTRIBUTING.md).
 */
class SectorSeedCallback(private val names: () -> List<String>, private val clock: Clock, private val ids: IdGenerator) : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
        val now = clock.nowMillis()
        val seen = mutableSetOf<String>()
        connection.prepare(
            "INSERT INTO Sector (id, name, normalizedName, sortOrder, createdAt) VALUES (?, ?, ?, ?, ?)"
        ).use { statement ->
            names().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, name ->
                val normalized = TextNormalizer.fold(name)
                if (seen.add(normalized)) {
                    statement.bindText(1, ids.newId())
                    statement.bindText(2, name)
                    statement.bindText(3, normalized)
                    statement.bindLong(4, index.toLong())
                    statement.bindLong(5, now)
                    statement.step()
                    statement.reset()
                }
            }
        }
    }
}

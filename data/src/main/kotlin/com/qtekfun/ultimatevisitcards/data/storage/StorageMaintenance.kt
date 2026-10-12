package com.qtekfun.ultimatevisitcards.data.storage

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import javax.inject.Inject

/**
 * Start-up maintenance (docs/05 §1): image folders of cards that are no longer in the database (an
 * abandoned capture, a crash between files and row), interrupted writes and restore staging are removed.
 * Runs when the process starts, before any capture can be in progress.
 */
class StorageMaintenance @Inject constructor(private val db: AppDatabase, private val store: ImageStore) {
    suspend fun run(): Set<String> = store.deleteOrphans(db.businessCardDao().all().map { it.id }.toSet())
}

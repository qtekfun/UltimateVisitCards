package com.qtekfun.ultimatevisitcards.data.repo

import androidx.room3.withWriteTransaction
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import javax.inject.Inject

/**
 * The search index is derived data: when it does not have exactly one row per contact (after the v2
 * migration recreated it, or any other mismatch) it is rebuilt. Runs at start-up, off the main thread.
 */
class SearchIndexHealth @Inject constructor(private val db: AppDatabase, private val indexer: SearchIndexer) {
    /** Returns true when the index had to be rebuilt. */
    suspend fun ensure(): Boolean = db.withWriteTransaction {
        val contacts = db.contactDao().allIds().size
        if (db.searchDao().count() == contacts) return@withWriteTransaction false
        indexer.rebuild()
        true
    }
}

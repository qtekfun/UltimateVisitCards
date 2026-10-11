package com.qtekfun.ultimatevisitcards.data.repo

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.TransactionRunner
import javax.inject.Inject

/** One write transaction around several repository calls; their own transactions nest inside it. */
class RoomTransactionRunner @Inject constructor(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> atomically(block: suspend () -> DomainResult<T>): DomainResult<T> = try {
        db.withWriteTransaction {
            when (val result = block()) {
                is DomainResult.Failure -> rollback(result)
                is DomainResult.Success -> result
            }
        }
    } catch (e: SQLiteException) {
        DomainResult.Failure(DomainError.Storage(e))
    }
}

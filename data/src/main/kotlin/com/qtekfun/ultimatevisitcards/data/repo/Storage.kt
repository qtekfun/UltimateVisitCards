package com.qtekfun.ultimatevisitcards.data.repo

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult

/**
 * Runs [block] in one write transaction and turns storage failures into [DomainError.Storage], so no
 * exception reaches the UI. Blocks validate before writing, so returning a [DomainResult.Failure] never
 * leaves a half-done change behind.
 */
internal suspend fun <T> AppDatabase.writeResult(block: suspend () -> DomainResult<T>): DomainResult<T> = try {
    withWriteTransaction { block() }
} catch (e: SQLiteException) {
    DomainResult.Failure(DomainError.Storage(e))
}

internal fun <T> T.success(): DomainResult<T> = DomainResult.Success(this)

internal fun failure(error: DomainError): DomainResult<Nothing> = DomainResult.Failure(error)

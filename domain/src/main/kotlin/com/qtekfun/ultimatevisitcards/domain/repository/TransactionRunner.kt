package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.DomainResult

/** Runs several repository writes as one unit: a [DomainResult.Failure] from [block] rolls everything back. */
interface TransactionRunner {
    suspend fun <T> atomically(block: suspend () -> DomainResult<T>): DomainResult<T>
}

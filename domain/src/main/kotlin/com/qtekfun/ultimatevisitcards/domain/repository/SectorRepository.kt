package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.model.SectorSummary
import kotlinx.coroutines.flow.Flow

interface SectorRepository {
    /** All sectors in display order. */
    fun observeAll(): Flow<List<Sector>>

    /** Sectors with their number of companies, in display order. */
    fun observeSummaries(): Flow<List<SectorSummary>>

    suspend fun get(id: String): Sector?

    /** Adds a sector at the end. Fails with `Invalid` for a blank name and `Conflict` if the name exists (ignoring case and accents). */
    suspend fun create(name: String): DomainResult<Sector>

    suspend fun rename(id: String, name: String): DomainResult<Sector>

    /** Deletes the sector; its companies keep existing without a sector. */
    suspend fun delete(id: String): DomainResult<Unit>
}

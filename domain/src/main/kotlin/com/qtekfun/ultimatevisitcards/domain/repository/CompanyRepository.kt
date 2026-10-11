package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import kotlinx.coroutines.flow.Flow

data class CompanyDraft(val name: String, val sectorId: String? = null, val country: String? = null, val website: String? = null, val notes: String? = null)

interface CompanyRepository {
    /** All companies by name. */
    fun observeAll(): Flow<List<Company>>

    fun observe(id: String): Flow<Company?>

    suspend fun get(id: String): Company?

    /** The company whose normalised name equals that of [name] (`ABC Marine GmbH` finds `ABC Marine`). */
    suspend fun findByName(name: String): Company?

    /** Fails with `Conflict` when another company has the same normalised name. */
    suspend fun create(draft: CompanyDraft): DomainResult<Company>

    suspend fun update(company: Company): DomainResult<Company>

    /** Deletes the company; its contacts keep existing without a company. */
    suspend fun delete(id: String): DomainResult<Unit>
}

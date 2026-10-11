package com.qtekfun.ultimatevisitcards.data.repo

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.data.db.toEntity
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCompanyRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: SearchIndexer,
    private val normalizer: CompanyNameNormalizer,
    private val clock: Clock,
    private val ids: IdGenerator
) : CompanyRepository {
    private val dao = db.companyDao()

    override fun observeAll(): Flow<List<Company>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: String): Flow<Company?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Company? = dao.get(id)?.toDomain()

    override suspend fun findByName(name: String): Company? =
        normalizer.normalize(name).takeIf { it.isNotEmpty() }?.let { dao.findByNormalizedName(it)?.toDomain() }

    override suspend fun create(draft: CompanyDraft): DomainResult<Company> = db.writeResult {
        val now = clock.nowMillis()
        val company = Company(
            id = ids.newId(),
            name = draft.name.trim(),
            sectorId = draft.sectorId,
            country = draft.country.clean(),
            website = draft.website.clean(),
            notes = draft.notes.clean(),
            createdAt = now,
            updatedAt = now
        )
        when (val name = validateName(company)) {
            is DomainResult.Failure -> name
            is DomainResult.Success -> {
                dao.insert(company.toEntity(name.value))
                company.success()
            }
        }
    }

    override suspend fun update(company: Company): DomainResult<Company> = db.writeResult {
        dao.get(company.id) ?: return@writeResult failure(DomainError.NotFound(company.id))
        val updated = company.copy(
            name = company.name.trim(),
            country = company.country.clean(),
            website = company.website.clean(),
            notes = company.notes.clean(),
            updatedAt = clock.nowMillis()
        )
        when (val name = validateName(updated)) {
            is DomainResult.Failure -> name
            is DomainResult.Success -> {
                dao.update(updated.toEntity(name.value))
                indexer.reindex(dao.contactIds(company.id))
                updated.success()
            }
        }
    }

    override suspend fun delete(id: String): DomainResult<Unit> = db.writeResult {
        val affected = dao.contactIds(id)
        if (dao.delete(id) == 0) return@writeResult failure(DomainError.NotFound(id))
        indexer.reindex(affected)
        Unit.success()
    }

    /** The normalised name, or why it cannot be used: empty, or taken by another company. */
    private suspend fun validateName(company: Company): DomainResult<String> {
        val normalized = normalizer.normalize(company.name)
        if (normalized.isEmpty()) return failure(DomainError.Invalid(DomainError.Field.NAME))
        val existing = dao.findByNormalizedName(normalized)
        if (existing != null && existing.id != company.id) {
            return failure(DomainError.Conflict(DomainError.Field.NAME, existing.id))
        }
        return normalized.success()
    }
}

internal fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

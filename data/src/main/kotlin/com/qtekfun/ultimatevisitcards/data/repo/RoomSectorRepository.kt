package com.qtekfun.ultimatevisitcards.data.repo

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.model.SectorSummary
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSectorRepository @Inject constructor(
    private val db: AppDatabase,
    private val indexer: SearchIndexer,
    private val clock: Clock,
    private val ids: IdGenerator
) : SectorRepository {
    private val dao = db.sectorDao()

    override fun observeAll(): Flow<List<Sector>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSummaries(): Flow<List<SectorSummary>> =
        dao.observeSummaries().map { rows -> rows.map { SectorSummary(it.sector.toDomain(), it.companyCount) } }

    override suspend fun get(id: String): Sector? = dao.get(id)?.toDomain()

    override suspend fun create(name: String): DomainResult<Sector> = db.writeResult {
        val trimmed = name.trim()
        val normalized = TextNormalizer.fold(trimmed)
        if (normalized.isEmpty()) return@writeResult failure(DomainError.Invalid(DomainError.Field.NAME))
        dao.findByNormalizedName(normalized)?.let {
            return@writeResult failure(DomainError.Conflict(DomainError.Field.NAME, it.id))
        }
        val entity = SectorEntity(
            id = ids.newId(),
            name = trimmed,
            normalizedName = normalized,
            sortOrder = (dao.maxSortOrder() ?: -1) + 1,
            createdAt = clock.nowMillis()
        )
        dao.insert(entity)
        entity.toDomain().success()
    }

    override suspend fun rename(id: String, name: String): DomainResult<Sector> = db.writeResult {
        val current = dao.get(id) ?: return@writeResult failure(DomainError.NotFound(id))
        val trimmed = name.trim()
        val normalized = TextNormalizer.fold(trimmed)
        if (normalized.isEmpty()) return@writeResult failure(DomainError.Invalid(DomainError.Field.NAME))
        dao.findByNormalizedName(normalized)?.takeIf { it.id != id }?.let {
            return@writeResult failure(DomainError.Conflict(DomainError.Field.NAME, it.id))
        }
        val renamed = current.copy(name = trimmed, normalizedName = normalized)
        dao.update(renamed)
        indexer.reindex(db.companyDao().contactIdsOfSector(id))
        renamed.toDomain().success()
    }

    override suspend fun delete(id: String): DomainResult<Unit> = db.writeResult {
        val affected = db.companyDao().contactIdsOfSector(id)
        if (dao.delete(id) == 0) return@writeResult failure(DomainError.NotFound(id))
        indexer.reindex(affected)
        Unit.success()
    }
}

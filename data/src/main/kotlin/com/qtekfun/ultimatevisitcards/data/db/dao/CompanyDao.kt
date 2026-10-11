package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow

data class CompanySummaryRow(@Embedded val company: CompanyEntity, val sectorName: String?, val contactCount: Int)

@Dao
interface CompanyDao {
    @Query("SELECT * FROM Company ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM Company WHERE id = :id")
    fun observe(id: String): Flow<CompanyEntity?>

    @Query(
        "SELECT co.*, s.name AS sectorName, (SELECT COUNT(*) FROM Contact c WHERE c.companyId = co.id) AS contactCount " +
            "FROM Company co LEFT JOIN Sector s ON s.id = co.sectorId ORDER BY co.name COLLATE NOCASE, co.id"
    )
    fun observeSummaries(): Flow<List<CompanySummaryRow>>

    @Query("SELECT COUNT(*) FROM Company")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM Company WHERE id = :id")
    suspend fun get(id: String): CompanyEntity?

    @Query("SELECT * FROM Company WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): CompanyEntity?

    @Insert
    suspend fun insert(company: CompanyEntity)

    @Update
    suspend fun update(company: CompanyEntity)

    @Query("DELETE FROM Company WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("SELECT id FROM Contact WHERE companyId = :companyId")
    suspend fun contactIds(companyId: String): List<String>

    @Query("SELECT c.id FROM Contact c JOIN Company co ON co.id = c.companyId WHERE co.sectorId = :sectorId")
    suspend fun contactIdsOfSector(sectorId: String): List<String>
}

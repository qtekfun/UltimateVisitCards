package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectorDao {
    @Query("SELECT * FROM Sector ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<SectorEntity>>

    @Query("SELECT * FROM Sector WHERE id = :id")
    suspend fun get(id: String): SectorEntity?

    @Query("SELECT * FROM Sector WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): SectorEntity?

    @Query("SELECT MAX(sortOrder) FROM Sector")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT COUNT(*) FROM Sector")
    suspend fun count(): Int

    @Insert
    suspend fun insert(sector: SectorEntity)

    @Update
    suspend fun update(sector: SectorEntity)

    @Query("DELETE FROM Sector WHERE id = :id")
    suspend fun delete(id: String): Int
}

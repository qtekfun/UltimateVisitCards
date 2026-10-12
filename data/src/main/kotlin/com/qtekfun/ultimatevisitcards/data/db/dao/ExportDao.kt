package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.EventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity

/** Whole tables, read inside one transaction to take a consistent backup snapshot. */
@Dao
interface ExportDao {
    @Query("SELECT * FROM Sector ORDER BY sortOrder, id")
    suspend fun sectors(): List<SectorEntity>

    @Query("SELECT * FROM Company ORDER BY id")
    suspend fun companies(): List<CompanyEntity>

    @Query("SELECT * FROM Event ORDER BY id")
    suspend fun events(): List<EventEntity>

    @Query("SELECT * FROM Contact ORDER BY id")
    suspend fun contacts(): List<ContactEntity>

    @Query("SELECT * FROM ContactEvent ORDER BY contactId, eventId")
    suspend fun contactEvents(): List<ContactEventEntity>

    @Query("SELECT * FROM BusinessCard ORDER BY id")
    suspend fun cards(): List<BusinessCardEntity>
}

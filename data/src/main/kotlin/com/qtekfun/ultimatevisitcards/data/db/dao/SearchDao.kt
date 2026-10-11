package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactFtsEntity

@Dao
interface SearchDao {
    @Insert
    suspend fun insert(row: ContactFtsEntity)

    @Query("DELETE FROM contact_fts WHERE contactId = :contactId")
    suspend fun delete(contactId: String)

    @Query("DELETE FROM contact_fts")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM contact_fts")
    suspend fun count(): Int
}

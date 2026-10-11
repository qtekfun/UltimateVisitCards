package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessCardDao {
    @Query("SELECT * FROM BusinessCard WHERE id = :id")
    suspend fun get(id: String): BusinessCardEntity?

    @Query("SELECT * FROM BusinessCard WHERE id = :id")
    fun observe(id: String): Flow<BusinessCardEntity?>

    @Query("SELECT * FROM BusinessCard WHERE contactId = :contactId ORDER BY capturedAt DESC, id")
    fun observeByContact(contactId: String): Flow<List<BusinessCardEntity>>

    @Query("SELECT * FROM BusinessCard WHERE contactId = :contactId ORDER BY capturedAt DESC, id")
    suspend fun byContact(contactId: String): List<BusinessCardEntity>

    @Query("SELECT * FROM BusinessCard WHERE contactId IS NULL AND ocrStatus IN (:statuses) ORDER BY capturedAt, id")
    fun observeWithoutContact(statuses: List<String>): Flow<List<BusinessCardEntity>>

    @Query("SELECT COUNT(*) FROM BusinessCard WHERE contactId IS NULL AND ocrStatus IN (:statuses)")
    fun observeCountWithoutContact(statuses: List<String>): Flow<Int>

    @Query("SELECT rawOcrText FROM BusinessCard WHERE contactId = :contactId ORDER BY capturedAt")
    suspend fun ocrTexts(contactId: String): List<String>

    @Insert
    suspend fun insert(card: BusinessCardEntity)

    @Query(
        "UPDATE BusinessCard SET ocrStatus = :status, rawOcrText = COALESCE(:rawOcrText, rawOcrText), " +
            "extractedJson = COALESCE(:extractedJson, extractedJson), ocrEngine = COALESCE(:ocrEngine, ocrEngine) " +
            "WHERE id = :id"
    )
    suspend fun updateOcr(id: String, status: String, rawOcrText: String?, extractedJson: String?, ocrEngine: String?): Int

    @Query("UPDATE BusinessCard SET eventId = :eventId WHERE id = :id")
    suspend fun setEvent(id: String, eventId: String?): Int

    @Query("UPDATE BusinessCard SET contactId = :contactId WHERE id = :id")
    suspend fun setContact(id: String, contactId: String): Int

    /** Leaves exactly one primary card for the contact: [primaryId]. */
    @Query("UPDATE BusinessCard SET isPrimary = (id = :primaryId) WHERE contactId = :contactId")
    suspend fun setPrimary(contactId: String, primaryId: String)

    @Query("DELETE FROM BusinessCard WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Every row (backup and restore). */
    @Query("SELECT * FROM BusinessCard")
    suspend fun all(): List<BusinessCardEntity>

    /** Restore in «replace everything» mode. */
    @Query("DELETE FROM BusinessCard")
    suspend fun deleteAll()
}

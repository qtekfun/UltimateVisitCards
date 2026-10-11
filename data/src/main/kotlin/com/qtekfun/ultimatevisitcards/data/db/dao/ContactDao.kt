package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Update
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactFtsEntity
import kotlinx.coroutines.flow.Flow

data class ContactWithCompanyRow(@Embedded val contact: ContactEntity, val companyName: String?)

data class ContactListRow(@Embedded val contact: ContactEntity, val companyName: String?, val effectiveCountry: String?, val primaryCardId: String?)

data class ContactEventName(val contactId: String, val name: String)

data class CompanySectorName(val companyId: String, val sectorName: String)

@Dao
interface ContactDao {
    @Query("SELECT * FROM Contact ORDER BY createdAt DESC, id")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM Contact WHERE id = :id")
    fun observe(id: String): Flow<ContactEntity?>

    @Query("SELECT * FROM Contact WHERE id = :id")
    suspend fun get(id: String): ContactEntity?

    @Query("SELECT * FROM Contact WHERE companyId = :companyId ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE, id")
    fun observeByCompany(companyId: String): Flow<List<ContactEntity>>

    @Query(
        "SELECT c.* FROM Contact c JOIN ContactEvent ce ON ce.contactId = c.id WHERE ce.eventId = :eventId " +
            "ORDER BY c.lastName COLLATE NOCASE, c.firstName COLLATE NOCASE, c.id"
    )
    fun observeByEvent(eventId: String): Flow<List<ContactEntity>>

    /** [match] is an FTS4 MATCH expression built by `SearchQuery`. */
    @Query(
        "SELECT * FROM Contact WHERE id IN (SELECT contactId FROM contact_fts WHERE contact_fts MATCH :match) " +
            "ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE, id"
    )
    fun search(match: String): Flow<List<ContactEntity>>

    @Query("SELECT id FROM Contact")
    suspend fun allIds(): List<String>

    /** The list query built by `ContactQueryBuilder` (filters are bound parameters, never concatenated). */
    @RawQuery(observedEntities = [ContactEntity::class, CompanyEntity::class, BusinessCardEntity::class, ContactEventEntity::class, ContactFtsEntity::class])
    fun observeList(query: RoomRawQuery): Flow<List<ContactListRow>>

    /** One-shot version of [observeList], for exports. */
    @RawQuery
    suspend fun list(query: RoomRawQuery): List<ContactListRow>

    @Query(
        "SELECT DISTINCT country FROM (SELECT COALESCE(c.country, co.country) AS country FROM Contact c " +
            "LEFT JOIN Company co ON co.id = c.companyId) WHERE country IS NOT NULL ORDER BY country COLLATE NOCASE"
    )
    fun observeCountries(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM Contact")
    fun observeCount(): Flow<Int>

    /** Every contact-event link with the event name, for exports (one query instead of one per contact). */
    @Query("SELECT ce.contactId AS contactId, e.name AS name FROM ContactEvent ce JOIN Event e ON e.id = ce.eventId ORDER BY e.name COLLATE NOCASE")
    suspend fun allEventNames(): List<ContactEventName>

    @Query("SELECT co.id AS companyId, s.name AS sectorName FROM Company co JOIN Sector s ON s.id = co.sectorId")
    suspend fun companySectorNames(): List<CompanySectorName>

    @Query("SELECT c.*, co.name AS companyName FROM Contact c LEFT JOIN Company co ON co.id = c.companyId")
    suspend fun allWithCompany(): List<ContactWithCompanyRow>

    @Insert
    suspend fun insert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("DELETE FROM Contact WHERE id = :id")
    suspend fun delete(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToEvent(link: ContactEventEntity)

    @Query("DELETE FROM ContactEvent WHERE contactId = :contactId AND eventId = :eventId")
    suspend fun removeFromEvent(contactId: String, eventId: String): Int

    @Query("SELECT eventId FROM ContactEvent WHERE contactId = :contactId")
    suspend fun eventIds(contactId: String): List<String>

    @Query(
        "SELECT e.name FROM Event e JOIN ContactEvent ce ON ce.eventId = e.id WHERE ce.contactId = :contactId " +
            "ORDER BY e.name"
    )
    suspend fun eventNames(contactId: String): List<String>

    @Query("SELECT contactId FROM ContactEvent WHERE eventId = :eventId")
    suspend fun contactIdsOfEvent(eventId: String): List<String>
}

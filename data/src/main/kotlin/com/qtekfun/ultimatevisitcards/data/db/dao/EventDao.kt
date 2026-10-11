package com.qtekfun.ultimatevisitcards.data.db.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatevisitcards.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

data class EventWithCount(@Embedded val event: EventEntity, val contactCount: Int)

@Dao
interface EventDao {
    /** The contact count is computed with COUNT over ContactEvent, never stored (docs/02-modelo-datos.md). */
    @Query(
        "SELECT e.*, (SELECT COUNT(*) FROM ContactEvent ce WHERE ce.eventId = e.id) AS contactCount " +
            "FROM Event e ORDER BY COALESCE(e.startDate, e.createdAt) DESC, e.name COLLATE NOCASE"
    )
    fun observeAllWithCount(): Flow<List<EventWithCount>>

    @Query("SELECT * FROM Event WHERE id = :id")
    fun observe(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM Event WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Insert
    suspend fun insert(event: EventEntity)

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM Event WHERE id = :id")
    suspend fun delete(id: String): Int
}

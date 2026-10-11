package com.qtekfun.ultimatevisitcards.domain.repository

import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import kotlinx.coroutines.flow.Flow

data class EventDraft(val name: String, val location: String? = null, val startDate: Long? = null, val endDate: Long? = null, val notes: String? = null)

interface EventRepository {
    /** Events with their contact count, most recent first (by start date, else creation). */
    fun observeAll(): Flow<List<EventSummary>>

    fun observe(id: String): Flow<Event?>

    fun observeCount(): Flow<Int>

    suspend fun get(id: String): Event?

    suspend fun create(draft: EventDraft): DomainResult<Event>

    suspend fun update(event: Event): DomainResult<Event>

    /** Deletes the event; contacts stay, cards keep existing without an event. */
    suspend fun delete(id: String): DomainResult<Unit>
}

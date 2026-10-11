package com.qtekfun.ultimatevisitcards.domain.model

/** A trade fair or any other group of contacts. */
data class Event(
    val id: String,
    val name: String,
    val location: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long
)

/** An event with its number of contacts (computed, never stored). */
data class EventSummary(val event: Event, val contactCount: Int)

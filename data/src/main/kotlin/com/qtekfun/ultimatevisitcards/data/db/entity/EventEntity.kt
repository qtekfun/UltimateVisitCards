package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "Event")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String?,
    val startDate: Long?,
    val endDate: Long?,
    val notes: String?,
    val createdAt: Long
)

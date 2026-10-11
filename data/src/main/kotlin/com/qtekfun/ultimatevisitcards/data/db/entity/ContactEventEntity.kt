package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/** Contact ↔ Event (N:M). */
@Entity(
    tableName = "ContactEvent",
    primaryKeys = ["contactId", "eventId"],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId")]
)
data class ContactEventEntity(val contactId: String, val eventId: String)

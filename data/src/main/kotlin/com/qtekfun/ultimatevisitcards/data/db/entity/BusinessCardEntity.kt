package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "BusinessCard",
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
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("contactId"), Index("ocrStatus"), Index("eventId")]
)
data class BusinessCardEntity(
    @PrimaryKey val id: String,
    /** Null while the card waits for review. */
    val contactId: String?,
    val eventId: String?,
    val frontOriginal: String,
    val frontProcessed: String,
    val backOriginal: String?,
    val backProcessed: String?,
    val frontQuad: String?,
    val backQuad: String?,
    val rawOcrText: String,
    val extractedJson: String?,
    val ocrEngine: String?,
    /** Name of an `OcrStatus` value. */
    val ocrStatus: String,
    val isPrimary: Boolean,
    val capturedAt: Long,
    val createdAt: Long
)

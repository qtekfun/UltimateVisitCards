package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "Company",
    foreignKeys = [
        ForeignKey(
            entity = SectorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectorId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["normalizedName"], unique = true), Index("sectorId")]
)
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Lower case, no accents, no legal form (GmbH, S.L., Ltd…). */
    val normalizedName: String,
    val sectorId: String?,
    val country: String?,
    val website: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * [normalizedName] (folded: no case, no accents) carries the uniqueness of the name: SQLite's NOCASE only
 * folds ASCII, so `Electrónica` and `ELECTRÓNICA` would otherwise both fit (see docs/decisions.md).
 */
@Entity(
    tableName = "Sector",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class SectorEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String, val sortOrder: Int, val createdAt: Long)

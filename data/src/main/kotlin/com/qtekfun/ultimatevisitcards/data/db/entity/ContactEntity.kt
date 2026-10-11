package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "Contact",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("companyId"),
        Index("email"),
        Index(value = ["lastName", "firstName"]),
        Index("createdAt")
    ]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val lastName: String,
    val jobTitle: String?,
    val companyId: String?,
    val country: String?,
    val phone: String?,
    val mobile: String?,
    val email: String?,
    val website: String?,
    val address: String?,
    val notes: String?,
    val exportedToPhoneAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

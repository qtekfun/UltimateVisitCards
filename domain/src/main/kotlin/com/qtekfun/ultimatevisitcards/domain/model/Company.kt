package com.qtekfun.ultimatevisitcards.domain.model

data class Company(
    val id: String,
    val name: String,
    val sectorId: String? = null,
    /** Normalised English country name, e.g. `South Korea`. */
    val country: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

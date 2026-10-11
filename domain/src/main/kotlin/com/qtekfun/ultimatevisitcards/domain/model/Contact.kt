package com.qtekfun.ultimatevisitcards.domain.model

data class Contact(
    val id: String,
    val firstName: String,
    val lastName: String,
    val jobTitle: String? = null,
    val companyId: String? = null,
    /** The contact's own country; the UI falls back to the company's when missing. */
    val country: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null,
    val notes: String? = null,
    /** When it was last added to the phone's contacts, to warn before adding it twice. */
    val exportedToPhoneAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

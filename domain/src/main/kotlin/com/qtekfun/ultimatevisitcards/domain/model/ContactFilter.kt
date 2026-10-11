package com.qtekfun.ultimatevisitcards.domain.model

/**
 * Combinable search and filters (docs/02 «Búsqueda dentro de un grupo y filtros combinables»): free text over
 * every searchable field plus first name, surname, job title, company, country, sector, event and date
 * range. Every non-empty part must match (AND). A fixed [eventId] turns the global search into a search
 * within that event.
 */
data class ContactFilter(
    val text: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val jobTitle: String = "",
    val companyId: String? = null,
    /** Normalised English name; matches the contact's country or, if missing, its company's. */
    val country: String? = null,
    val sectorId: String? = null,
    val eventId: String? = null,
    /** Contact creation date range, epoch millis UTC, inclusive. */
    val from: Long? = null,
    val to: Long? = null,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && firstName.isBlank() && lastName.isBlank() && jobTitle.isBlank() &&
            companyId == null && country == null && sectorId == null && eventId == null && from == null && to == null

    /** Number of active filters besides the free text (for the «Filters (N)» button). */
    val activeFilters: Int
        get() = listOf(firstName.isNotBlank(), lastName.isNotBlank(), jobTitle.isNotBlank(), companyId != null, country != null, sectorId != null, eventId != null, from != null || to != null)
            .count { it }
}

/** A row of the contact list or card grid. */
data class ContactListItem(
    val contact: Contact,
    val companyName: String?,
    /** The contact's country or, if missing, its company's (docs/02). */
    val country: String?,
    /** Card shown for the contact (primary, else newest), for the thumbnail. */
    val primaryCardId: String?,
)

data class CompanySummary(val company: Company, val sectorName: String?, val contactCount: Int)

data class SectorSummary(val sector: Sector, val companyCount: Int)

/** «Total contacts · Events · Companies» on the home screen. */
data class Stats(val contacts: Int, val events: Int, val companies: Int)

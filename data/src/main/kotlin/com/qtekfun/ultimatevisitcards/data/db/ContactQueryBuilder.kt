package com.qtekfun.ultimatevisitcards.data.db

import androidx.room3.RoomRawQuery
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.search.SearchQuery

/**
 * Builds the contact list query for a [ContactFilter] (docs/02, «Búsqueda dentro de un grupo y filtros
 * combinables»). Only the shape of the SQL depends on the filter; every value is a bound parameter.
 */
object ContactQueryBuilder {
    fun build(filter: ContactFilter): RoomRawQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val match = SearchQuery.toFtsMatch(
            filter.text,
            buildMap {
                if (filter.firstName.isNotBlank()) put("firstName", filter.firstName)
                if (filter.lastName.isNotBlank()) put("lastName", filter.lastName)
                if (filter.jobTitle.isNotBlank()) put("jobTitle", filter.jobTitle)
            }
        )
        if (match != null) {
            where += "c.id IN (SELECT contactId FROM contact_fts WHERE contact_fts MATCH ?)"
            args += match
        }
        filter.eventId?.let {
            where += "c.id IN (SELECT contactId FROM ContactEvent WHERE eventId = ?)"
            args += it
        }
        filter.country?.let {
            where += "COALESCE(c.country, co.country) = ?"
            args += it
        }
        filter.sectorId?.let {
            where += "co.sectorId = ?"
            args += it
        }
        filter.companyId?.let {
            where += "c.companyId = ?"
            args += it
        }
        filter.from?.let {
            where += "c.createdAt >= ?"
            args += it
        }
        filter.to?.let {
            where += "c.createdAt <= ?"
            args += it
        }
        // A free-text search with nothing searchable left («*», «"») matches nothing rather than everything.
        val impossible =
            match == null && (filter.text.isNotBlank() || filter.firstName.isNotBlank() || filter.lastName.isNotBlank() || filter.jobTitle.isNotBlank())
        if (impossible) where += "0"
        val order = if (filter.isEmpty) "c.createdAt DESC, c.id" else "c.lastName COLLATE NOCASE, c.firstName COLLATE NOCASE, c.id"
        val sql = buildString {
            append("SELECT c.*, co.name AS companyName, COALESCE(c.country, co.country) AS effectiveCountry, ")
            append("(SELECT b.id FROM BusinessCard b WHERE b.contactId = c.id ORDER BY b.isPrimary DESC, b.capturedAt DESC LIMIT 1) AS primaryCardId ")
            append("FROM Contact c LEFT JOIN Company co ON co.id = c.companyId")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY ").append(order)
        }
        return RoomRawQuery(sql) { statement ->
            args.forEachIndexed { i, value ->
                when (value) {
                    is String -> statement.bindText(i + 1, value)
                    is Long -> statement.bindLong(i + 1, value)
                    else -> error("Unsupported argument $value")
                }
            }
        }
    }

    /** The same rows for an explicit selection of contacts (at most [MAX_IDS] ids per query), by surname. */
    fun byIds(ids: List<String>): RoomRawQuery {
        require(ids.size in 1..MAX_IDS) { "Between 1 and $MAX_IDS ids per query" }
        val sql = "SELECT c.*, co.name AS companyName, COALESCE(c.country, co.country) AS effectiveCountry, " +
            "(SELECT b.id FROM BusinessCard b WHERE b.contactId = c.id ORDER BY b.isPrimary DESC, b.capturedAt DESC LIMIT 1) AS primaryCardId " +
            "FROM Contact c LEFT JOIN Company co ON co.id = c.companyId WHERE c.id IN (${ids.joinToString(",") { "?" }}) " +
            "ORDER BY c.lastName COLLATE NOCASE, c.firstName COLLATE NOCASE, c.id"
        return RoomRawQuery(sql) { statement -> ids.forEachIndexed { i, id -> statement.bindText(i + 1, id) } }
    }

    /** Well under SQLite's limit of bound parameters per statement. */
    const val MAX_IDS = 500
}

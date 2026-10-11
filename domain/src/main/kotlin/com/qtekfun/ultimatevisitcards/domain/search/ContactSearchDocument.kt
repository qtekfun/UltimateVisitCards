package com.qtekfun.ultimatevisitcards.domain.search

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer

/** Everything a contact can be found by, folded for the FTS index (one row of `contact_fts`). */
data class ContactSearchDocument(
    val contactId: String,
    val name: String,
    val company: String,
    val jobTitle: String,
    val country: String,
    val sector: String,
    val email: String,
    val phone: String,
    val notes: String,
    val ocrText: String,
    val events: String
) {
    companion object {
        /**
         * Builds the document from a contact and the names of what it is linked to. The country falls back
         * to the company's, as in the UI.
         */
        fun of(
            contact: Contact,
            companyName: String?,
            companyCountry: String?,
            sectorName: String?,
            eventNames: List<String>,
            ocrTexts: List<String>
        ): ContactSearchDocument = ContactSearchDocument(
            contactId = contact.id,
            name = fold(contact.firstName, contact.lastName),
            company = fold(companyName),
            jobTitle = fold(contact.jobTitle),
            country = fold(contact.country ?: companyCountry),
            sector = fold(sectorName),
            email = fold(contact.email),
            phone = phoneTerms(listOfNotNull(contact.phone, contact.mobile)),
            notes = fold(contact.notes),
            ocrText = fold(*ocrTexts.toTypedArray()),
            events = fold(*eventNames.toTypedArray())
        )

        private fun fold(vararg parts: String?): String = TextNormalizer.fold(parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" "))

        /**
         * A phone is found by any of its printed groups, by all its digits and, for international numbers,
         * by the national number (`+34 600 12 34 56` is found with `600123456`). The country code length
         * is unknown (1-3 digits), so the three candidates are indexed.
         */
        internal fun phoneTerms(numbers: List<String>): String = numbers.flatMap { number ->
            val digits = number.filter { it.isDigit() }
            val international = number.trimStart().let { it.startsWith("+") || it.startsWith("00") }
            val withoutPrefix = if (number.trimStart().startsWith("00")) digits.drop(2) else digits
            val national = if (international) (1..3).map { withoutPrefix.drop(it) } else emptyList()
            TextNormalizer.tokens(number) + digits + withoutPrefix + national
        }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
    }
}

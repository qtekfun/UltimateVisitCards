package com.qtekfun.ultimatevisitcards.domain.dedupe

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import javax.inject.Inject
import kotlin.math.min

/** What a new contact is compared by. */
data class ContactProbe(val firstName: String, val lastName: String, val companyName: String?, val email: String?, val phone: String?, val mobile: String?)

/** Why an existing contact may be the same person (docs/06-duplicados-historico.md §2). */
enum class MatchReason(val points: Int) {
    EMAIL(100),
    NAME_AND_COMPANY(95),
    PHONE(90),
    FIRST_NAME_AND_COMPANY(70),
    NAME(60),
    INITIAL_AND_COMPANY(55)
}

data class DuplicateCandidate(val contact: Contact, val companyName: String?, val reason: MatchReason) {
    val score: Int get() = reason.points
}

/**
 * Finds existing contacts that may be the person being saved. Each candidate gets the best of the rules;
 * candidates scoring 55 or more are returned, best first, at most [MAX_CANDIDATES]. It never blocks a
 * save: the review shows a warning and lets the user decide.
 */
class DuplicateDetector @Inject constructor() {
    private val companies = CompanyNameNormalizer.fromBundledDictionary()

    fun find(probe: ContactProbe, existing: List<ContactWithCompany>): List<DuplicateCandidate> {
        val new = Normalised.of(probe.firstName, probe.lastName, probe.companyName, probe.email, listOfNotNull(probe.phone, probe.mobile), companies)
        if (new.isEmpty()) return emptyList()
        return existing.mapNotNull { candidate ->
            val old = Normalised.of(
                candidate.contact.firstName,
                candidate.contact.lastName,
                candidate.companyName,
                candidate.contact.email,
                listOfNotNull(candidate.contact.phone, candidate.contact.mobile),
                companies
            )
            bestReason(new, old)?.let { DuplicateCandidate(candidate.contact, candidate.companyName, it) }
        }.sortedWith(compareByDescending<DuplicateCandidate> { it.score }.thenBy { it.contact.lastName }).take(MAX_CANDIDATES)
    }

    private fun bestReason(a: Normalised, b: Normalised): MatchReason? {
        val reasons = mutableListOf<MatchReason>()
        if (a.email != null && a.email == b.email) reasons += MatchReason.EMAIL
        if (a.phones.any { it in b.phones }) reasons += MatchReason.PHONE
        val sameCompany = a.company != null && a.company == b.company
        val sameFirst = a.first.isNotEmpty() && a.first == b.first
        val sameLast = a.last.isNotEmpty() && a.last == b.last
        if (sameFirst && sameLast) reasons += if (sameCompany) MatchReason.NAME_AND_COMPANY else MatchReason.NAME
        if (sameFirst && sameCompany && !sameLast && a.last.isNotEmpty() && b.last.isNotEmpty() && editDistance(a.last, b.last) <= 1) {
            reasons += MatchReason.FIRST_NAME_AND_COMPANY
        }
        if (sameLast && sameCompany && !sameFirst && initialMatches(a.first, b.first)) reasons += MatchReason.INITIAL_AND_COMPANY
        return reasons.maxByOrNull { it.points }
    }

    /** «J.» / «J» against «John». */
    private fun initialMatches(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty() || a[0] != b[0]) return false
        return a.length == 1 || b.length == 1
    }

    private class Normalised(val first: String, val last: String, val company: String?, val email: String?, val phones: Set<String>) {
        fun isEmpty() = first.isEmpty() && last.isEmpty() && email == null && phones.isEmpty()

        companion object {
            fun of(first: String, last: String, company: String?, email: String?, phones: List<String>, companies: CompanyNameNormalizer) = Normalised(
                first = words(first),
                last = words(last),
                company = company?.let { companies.normalize(it) }?.takeIf { it.isNotEmpty() },
                email = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
                // Last 9 digits: the same number with or without the country prefix.
                phones = phones.map { p -> p.filter { it.isDigit() }.takeLast(9) }.filter { it.length >= 7 }.toSet()
            )

            /** Folded, without punctuation, single spaces: «Jean-Pierre» = «jean pierre», «J.» = «j». */
            private fun words(text: String) = TextNormalizer.tokens(text).joinToString(" ")
        }
    }

    companion object {
        const val MAX_CANDIDATES = 3

        internal fun editDistance(a: String, b: String): Int {
            var previous = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val current = IntArray(b.length + 1)
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                }
                previous = current
            }
            return previous[b.length]
        }
    }
}

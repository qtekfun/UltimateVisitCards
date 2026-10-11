package com.qtekfun.ultimatevisitcards.domain.dedupe

import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import javax.inject.Inject

/** Two contacts that may be the same person. */
data class DuplicatePair(val a: ContactWithCompany, val b: ContactWithCompany, val reason: MatchReason) {
    val score: Int get() = reason.points
}

/**
 * «Posibles duplicados» (docs/06 §4): every pair of existing contacts the [DuplicateDetector] rules would flag
 * (score ≥ 55), each pair once, best first. Instead of comparing all pairs, contacts are grouped by the keys
 * the rules need (email, phone, first + last name, first name + company, last name + company) and only
 * compared within a group, so thousands of contacts take milliseconds.
 */
class DuplicateScanner @Inject constructor(private val detector: DuplicateDetector) {
    fun scan(contacts: List<ContactWithCompany>): List<DuplicatePair> {
        val normalised = contacts.map { detector.normalise(it) }
        val buckets = HashMap<String, MutableList<Int>>()
        normalised.forEachIndexed { i, n ->
            if (!n.isEmpty()) keys(n).forEach { buckets.getOrPut(it) { mutableListOf() } += i }
        }
        val seen = HashSet<Long>()
        val pairs = mutableListOf<DuplicatePair>()
        for (members in buckets.values) {
            if (members.size < 2) continue
            for (x in members.indices) {
                for (y in x + 1 until members.size) {
                    val i = minOf(members[x], members[y])
                    val j = maxOf(members[x], members[y])
                    if (!seen.add(i.toLong() * contacts.size + j)) continue
                    detector.bestReason(normalised[i], normalised[j])?.let { pairs += DuplicatePair(contacts[i], contacts[j], it) }
                }
            }
        }
        return pairs.sortedWith(compareByDescending<DuplicatePair> { it.score }.thenBy { name(it.a) }.thenBy { name(it.b) })
    }

    /** Every rule needs both contacts to share at least one of these keys. */
    private fun keys(n: DuplicateDetector.Normalised): List<String> = buildList {
        n.email?.let { add("e:$it") }
        n.phones.forEach { add("p:$it") }
        if (n.first.isNotEmpty() && n.last.isNotEmpty()) add("n:${n.first}|${n.last}")
        if (n.company != null) {
            if (n.first.isNotEmpty()) add("fc:${n.first}|${n.company}")
            if (n.last.isNotEmpty()) add("lc:${n.last}|${n.company}")
        }
    }

    private fun name(c: ContactWithCompany) = "${c.contact.lastName} ${c.contact.firstName}"
}

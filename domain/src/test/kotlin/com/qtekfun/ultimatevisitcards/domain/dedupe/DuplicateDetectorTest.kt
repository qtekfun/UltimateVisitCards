package com.qtekfun.ultimatevisitcards.domain.dedupe

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** The rule table of docs/06 §2: positives, negatives and limits. Synthetic people. */
class DuplicateDetectorTest {
    private val detector = DuplicateDetector()

    private fun existing(
        id: String,
        first: String,
        last: String,
        company: String? = null,
        email: String? = null,
        phone: String? = null,
        mobile: String? = null
    ) = ContactWithCompany(Contact(id, first, last, email = email, phone = phone, mobile = mobile, createdAt = 0, updatedAt = 0), company)

    private fun probe(first: String = "", last: String = "", company: String? = null, email: String? = null, phone: String? = null, mobile: String? = null) =
        ContactProbe(first, last, company, email, phone, mobile)

    private fun reason(probe: ContactProbe, vararg contacts: ContactWithCompany) = detector.find(probe, contacts.toList()).firstOrNull()?.reason

    @Test
    fun `same email, whatever the name`() {
        assertEquals(MatchReason.EMAIL, reason(probe("X", "Y", email = "John@ABCMarine.com "), existing("1", "John", "Smith", email = "john@abcmarine.com")))
    }

    @Test
    fun `same phone by its last nine digits, crossing phone and mobile`() {
        val old = existing("1", "John", "Smith", mobile = "+34 600 123 456")
        assertEquals(MatchReason.PHONE, reason(probe("Juan", "Smith", phone = "600 12 34 56"), old))
        assertEquals(MatchReason.PHONE, reason(probe(mobile = "0034600123456"), old))
        assertEquals(null, reason(probe(phone = "600 123 457"), old))
    }

    @Test
    fun `name, surname and company`() {
        val old = existing("1", "José", "García", company = "ABC Marine GmbH")
        assertEquals(MatchReason.NAME_AND_COMPANY, reason(probe("Jose", "GARCIA", company = "abc marine"), old))
        assertEquals(MatchReason.NAME, reason(probe("Jose", "Garcia", company = "Other Ltd"), old))
        assertEquals(MatchReason.NAME, reason(probe("Jose", "Garcia"), old))
    }

    @Test
    fun `first name and company with a near surname`() {
        val old = existing("1", "Anna", "Schmidt", company = "Elbe GmbH")
        assertEquals(MatchReason.FIRST_NAME_AND_COMPANY, reason(probe("Anna", "Schmid", company = "Elbe"), old))
        assertEquals(null, reason(probe("Anna", "Schneider", company = "Elbe"), old))
    }

    @Test
    fun `initial and surname at the same company`() {
        val old = existing("1", "John", "Smith", company = "ABC Marine")
        assertEquals(MatchReason.INITIAL_AND_COMPANY, reason(probe("J.", "Smith", company = "ABC Marine Ltd"), old))
        assertEquals(null, reason(probe("J.", "Smith", company = "XYZ"), old))
        assertEquals(null, reason(probe("Peter", "Smith", company = "ABC Marine"), old))
    }

    @Test
    fun `the best rule wins and candidates are ranked, three at most`() {
        val contacts = listOf(
            existing("a", "John", "Smith"),
            existing("b", "John", "Smith", company = "ABC"),
            existing("c", "Other", "Person", email = "john@abc.com"),
            existing("d", "John", "Smith", company = "XYZ"),
            existing("e", "Mary", "Jones")
        )
        val found = detector.find(probe("John", "Smith", company = "ABC", email = "john@abc.com"), contacts)
        assertEquals(listOf("c", "b"), found.take(2).map { it.contact.id })
        assertEquals(3, found.size)
        assertTrue(found.none { it.contact.id == "e" })
    }

    @Test
    fun `a common name alone never goes above 60`() {
        val found = detector.find(probe("Maria", "Garcia"), (1..5).map { existing("$it", "María", "García") })
        assertTrue(found.all { it.score == 60 })
    }

    @Test
    fun `an empty probe matches nothing`() {
        assertEquals(emptyList(), detector.find(probe(company = "ABC"), listOf(existing("1", "", "", company = "ABC"))))
    }

    @Test
    fun `edit distance`() {
        assertEquals(0, DuplicateDetector.editDistance("smith", "smith"))
        assertEquals(1, DuplicateDetector.editDistance("smith", "smyth"))
        assertEquals(1, DuplicateDetector.editDistance("schmid", "schmidt"))
        assertEquals(3, DuplicateDetector.editDistance("abc", ""))
    }
}

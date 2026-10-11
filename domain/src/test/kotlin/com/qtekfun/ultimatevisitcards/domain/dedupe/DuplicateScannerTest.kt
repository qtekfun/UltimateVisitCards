package com.qtekfun.ultimatevisitcards.domain.dedupe

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.repository.ContactWithCompany
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DuplicateScannerTest {
    private val detector = DuplicateDetector()
    private val scanner = DuplicateScanner(detector)

    private fun c(id: String, first: String, last: String, company: String? = null, email: String? = null, phone: String? = null) =
        ContactWithCompany(Contact(id, first, last, email = email, phone = phone, createdAt = 0, updatedAt = 0), company)

    private fun ids(p: DuplicatePair) = setOf(p.a.contact.id, p.b.contact.id)

    @Test
    fun `finds each pair once, best first`() {
        val list = listOf(
            c("1", "John", "Smith", "ABC Marine", email = "john@abc.com"),
            c("2", "John", "Smith", "ABC Marine GmbH"),
            c("3", "J.", "Smith", "ABC Marine"),
            c("4", "Other", "Person", email = "john@abc.com"),
            c("5", "Mary", "Jones")
        )
        val pairs = scanner.scan(list)
        val found = pairs.map { ids(it) }
        assertEquals(found.size, found.toSet().size)
        assertEquals(setOf("1", "4"), found.first())
        assertEquals(MatchReason.EMAIL, pairs.first().reason)
        assertTrue(setOf("1", "2") in found)
        assertTrue(setOf("2", "3") in found)
        assertTrue(pairs.none { "5" in ids(it) })
        assertTrue(pairs.zipWithNext().all { (x, y) -> x.score >= y.score })
    }

    @Test
    fun `same pairs and reasons as comparing every pair`() {
        val random = Random(7)
        val firsts = listOf("Ana", "Luis", "J.", "John", "Marta")
        val lasts = listOf("Ruiz", "Ruis", "Gil", "Smith", "Smyth")
        val companies = listOf(null, "ABC Marine", "ABC Marine S.L.", "XYZ")
        val list = (0 until 120).map { i ->
            c(
                "$i",
                firsts.random(random),
                lasts.random(random),
                companies.random(random),
                email = if (random.nextInt(5) == 0) "x${random.nextInt(4)}@a.com" else null,
                phone = if (random.nextInt(5) == 0) "+34 600 00${random.nextInt(4)} 000" else null
            )
        }
        val naive = buildSet {
            for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    detector.bestReason(detector.normalise(list[i]), detector.normalise(list[j]))?.let {
                        add(Triple(list[i].contact.id, list[j].contact.id, it))
                    }
                }
            }
        }
        val scanned = scanner.scan(list).map { p ->
            val (x, y) = listOf(p.a.contact.id, p.b.contact.id).sortedBy { it.toInt() }
            Triple(x, y, p.reason)
        }.toSet()
        assertTrue(naive.isNotEmpty())
        assertEquals(naive, scanned)
    }

    @Test
    fun `5000 contacts in well under two seconds`() {
        val random = Random(42)
        val list = (0 until 5000).map { i ->
            c(
                "$i",
                "First${random.nextInt(800)}",
                "Last${random.nextInt(1500)}",
                "Company${random.nextInt(300)}",
                email = "p$i@example.com",
                phone = "+34 6${10_000_000 + i}"
            )
        }
        val start = System.nanoTime()
        scanner.scan(list)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue(millis < 2000, "took $millis ms")
    }

    @Test
    fun `contacts with nothing to compare are ignored`() {
        assertEquals(emptyList<DuplicatePair>(), scanner.scan(listOf(c("1", "", ""), c("2", "", ""))))
    }
}

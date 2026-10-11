package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * docs/07 fase 10: search over 5 000 synthetic contacts must answer in under 100 ms. Runs on the JVM with
 * the bundled SQLite (the same engine as on the phone); the medians are written to the test output and
 * copied to docs/07 «Resultados de medición». A phone is slower than a CI machine, hence the margin
 * (the budget is checked against the median, the worst run is only reported).
 */
@RunWith(AndroidJUnit4::class)
class SearchPerformanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    private val firstNames =
        listOf("Ana", "Luis", "Marta", "Jorge", "Lucía", "Pablo", "Elena", "Iñaki", "Sofía", "Hugo", "Ji-ho", "Akira", "Chloé", "Lukas", "Giulia")
    private val lastNames = listOf("García", "Muñoz", "López", "Sánchez", "Pérez", "Park", "Tanaka", "Martin", "Schmidt", "Rossi", "Núñez", "Ortega", "Vidal")
    private val titles =
        listOf("Director Comercial", "Sales Manager", "Ingeniera Naval", "Purchasing Manager", "CEO", "Technical Director", "Responsabile Vendite")
    private val countries = listOf("Spain", "Germany", "Italy", "South Korea", "Japan", "France", "Norway")

    private suspend fun fill(contacts: Int) {
        val sectors = (1..10).map { data.sectors.create("Sector demo $it").value() }
        val companies = (1..200).map { i ->
            data.companies.create(CompanyDraft("Empresa Demo $i S.L.", sectors[i % sectors.size].id, countries[i % countries.size])).value()
        }
        val events = (1..8).map { data.events.create(EventDraft("Feria Demo $it")).value() }
        repeat(contacts) { i ->
            val contact = data.contacts.create(
                ContactDraft(
                    firstNames[i % firstNames.size],
                    "${lastNames[(i / firstNames.size) % lastNames.size]} $i",
                    jobTitle = titles[i % titles.size],
                    companyId = companies[i % companies.size].id,
                    email = "contacto$i@example.com",
                    phone = "+34 600 ${(100_000 + i).toString().chunked(3).joinToString(" ")}",
                    notes = if (i % 10 == 0) "Interesado en propulsión eléctrica" else null
                )
            ).value()
            data.contacts.addToEvent(contact.id, events[i % events.size].id).value()
        }
    }

    /** Median and worst of [runs] timed runs, after one warm-up, in milliseconds. */
    private suspend fun measure(name: String, filter: ContactFilter, runs: Int = 7): Double {
        data.contacts.observeList(filter).first()
        val times = (1..runs).map {
            val start = System.nanoTime()
            val found = data.contacts.observeList(filter).first()
            ((System.nanoTime() - start) / 1e6).also { check(found.isNotEmpty()) { "$name found nothing" } }
        }.sorted()
        val median = times[runs / 2]
        println("SEARCH-PERF | $name | median %.1f ms | worst %.1f ms".format(median, times.last()))
        return median
    }

    @Test
    fun `search over 5000 contacts answers in under 100 ms`() = runTest(dispatcher, timeout = 8.minutes) {
        val start = System.nanoTime()
        fill(5_000)
        println("SEARCH-PERF | fill 5000 contacts | %.1f s".format((System.nanoTime() - start) / 1e9))
        val company = data.companies.observeAll().first().first()
        val event = data.events.observeAll().first().first().event
        val medians = listOf(
            measure("all contacts (no filter)", ContactFilter()),
            measure("text: one word (sánchez)", ContactFilter(text = "sanchez")),
            measure("text: prefix (garc)", ContactFilter(text = "garc")),
            measure("text: two words (propulsion electrica)", ContactFilter(text = "propulsion electrica")),
            measure("text + company", ContactFilter(text = "director", companyId = company.id)),
            measure("event + country", ContactFilter(eventId = event.id, country = "Spain")),
            measure("first name + last name", ContactFilter(firstName = "lucia", lastName = "lopez"))
        )
        medians.forEach { assertTrue(it < 100.0, "median $it ms over the 100 ms budget") }
    }
}

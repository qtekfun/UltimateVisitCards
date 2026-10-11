package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The combinable filters of docs/02 over a synthetic trade-fair dataset, including the scenario every design
 * decision is measured against: «Event = SMM 2026 + Sector = Shipyard + Country = South Korea».
 */
@RunWith(AndroidJUnit4::class)
class ContactListQueryTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    private class World(
        val smm: Event,
        val navalia: Event,
        val shipyard: Sector,
        val owner: Sector,
        val hhi: Company,
        val lines: Company,
        val astilleros: Company
    )

    /** 3 companies × sectors × countries, 6 contacts, 2 events. */
    private suspend fun world(): World {
        val shipyard = data.sectors.create("Astillero").value()
        val owner = data.sectors.create("Armador").value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val navalia = data.events.create(EventDraft("Navalia")).value()
        val hhi = data.companies.create(CompanyDraft("Example Heavy Industries Co., Ltd.", shipyard.id, "South Korea")).value()
        val lines = data.companies.create(CompanyDraft("Sample Lines", owner.id, "South Korea")).value()
        val astilleros = data.companies.create(CompanyDraft("Astilleros Ficticios S.A.", shipyard.id, "Spain")).value()
        suspend fun person(first: String, last: String, company: Company?, title: String? = null, country: String? = null, vararg events: Event) {
            val c = data.contacts.create(ContactDraft(first, last, jobTitle = title, companyId = company?.id, country = country)).value()
            events.forEach { data.contacts.addToEvent(c.id, it.id).value() }
        }
        person("Ji-ho", "Park", hhi, "Sales Director", null, smm)
        person("Min-jun", "Kim", hhi, "Chief Engineer", null, smm, navalia)
        person("Seo-yeon", "Lee", lines, "Fleet Manager", null, smm)
        person("Lucía", "García", astilleros, "Directora Comercial", null, smm)
        person("Pedro", "Gómez", astilleros, "Ingeniero", "Portugal", navalia)
        person("Ana", "Ruiz", null, "Consultora", "Spain")
        return World(smm, navalia, shipyard, owner, hhi, lines, astilleros)
    }

    private suspend fun names(filter: ContactFilter) = data.contacts.observeList(filter).first().map { it.contact.lastName }

    @Test
    fun `the trade fair scenario - event, sector and country`() = runTest(dispatcher) {
        val w = world()
        assertEquals(listOf("Kim", "Park"), names(ContactFilter(eventId = w.smm.id, sectorId = w.shipyard.id, country = "South Korea")))
    }

    @Test
    fun `without filters everything, newest first`() = runTest(dispatcher) {
        world()
        assertEquals(listOf("Ruiz", "Gómez", "García", "Lee", "Kim", "Park"), names(ContactFilter()))
    }

    @Test
    fun `each filter alone`() = runTest(dispatcher) {
        val w = world()
        assertEquals(listOf("García", "Kim", "Lee", "Park"), names(ContactFilter(eventId = w.smm.id)))
        assertEquals(listOf("García", "Gómez", "Kim", "Park"), names(ContactFilter(sectorId = w.shipyard.id)))
        assertEquals(listOf("Kim", "Park"), names(ContactFilter(companyId = w.hhi.id)))
        assertEquals(listOf("Kim", "Lee", "Park"), names(ContactFilter(country = "South Korea")))
        assertEquals(listOf("Lee"), names(ContactFilter(jobTitle = "fleet")))
        assertEquals(listOf("García"), names(ContactFilter(firstName = "lucia")))
        assertEquals(listOf("Gómez"), names(ContactFilter(lastName = "gomez")))
    }

    @Test
    fun `the contact's own country wins over its company's`() = runTest(dispatcher) {
        val w = world()
        // Gómez works for a Spanish company but is in Portugal; Ruiz has no company.
        assertEquals(listOf("García"), names(ContactFilter(country = "Spain", companyId = w.astilleros.id)))
        assertEquals(listOf("García", "Ruiz"), names(ContactFilter(country = "Spain")))
        assertEquals(listOf("Gómez"), names(ContactFilter(country = "Portugal")))
        assertEquals(listOf("Portugal", "South Korea", "Spain"), data.contacts.observeCountries().first())
    }

    @Test
    fun `free text and field filters combine`() = runTest(dispatcher) {
        val w = world()
        assertEquals(listOf("Kim"), names(ContactFilter(text = "engineer", eventId = w.navalia.id, country = "South Korea")))
        assertEquals(emptyList(), names(ContactFilter(text = "engineer", sectorId = w.owner.id)))
        // «Nombre» and «Apellidos» look at their own column only.
        assertEquals(emptyList(), names(ContactFilter(firstName = "garcia")))
        assertEquals(listOf("Park"), names(ContactFilter(text = "sales", firstName = "ji")))
    }

    @Test
    fun `date range on the creation date`() = runTest(dispatcher) {
        world()
        val all = data.contacts.observeList(ContactFilter()).first().sortedBy { it.contact.createdAt }
        val from = all[2].contact.createdAt
        val to = all[3].contact.createdAt
        assertEquals(listOf(all[2], all[3]).map { it.contact.lastName }.sorted(), names(ContactFilter(from = from, to = to)).sorted())
    }

    @Test
    fun `unsearchable text matches nothing instead of everything`() = runTest(dispatcher) {
        world()
        assertEquals(emptyList(), names(ContactFilter(text = "*\"")))
    }

    @Test
    fun `items carry company, effective country and the primary card`() = runTest(dispatcher) {
        val w = world()
        val park = data.contacts.observeList(ContactFilter(lastName = "park")).first().single()
        assertEquals("Example Heavy Industries Co., Ltd.", park.companyName)
        assertEquals("South Korea", park.country)
        assertEquals(null, park.primaryCardId)
        val card = data.cards.create(BusinessCardDraft("o", "p", capturedAt = 1)).value()
        data.cards.attachToContact(card.id, park.contact.id).value()
        assertEquals(card.id, data.contacts.observeList(ContactFilter(lastName = "park")).first().single().primaryCardId)
    }

    @Test
    fun `the list follows changes`() = runTest(dispatcher) {
        val w = world()
        data.contacts.observeList(ContactFilter(sectorId = w.owner.id)).test {
            assertEquals(listOf("Lee"), awaitItem().map { it.contact.lastName })
            // Moving a company to another sector changes the result.
            data.companies.update(w.hhi.copy(sectorId = w.owner.id)).value()
            assertEquals(listOf("Kim", "Lee", "Park"), awaitItem().map { it.contact.lastName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summaries and counts`() = runTest(dispatcher) {
        val w = world()
        val companies = data.companies.observeSummaries().first().associate { it.company.name to (it.sectorName to it.contactCount) }
        assertEquals("Astillero" to 2, companies["Example Heavy Industries Co., Ltd."])
        assertEquals("Armador" to 1, companies["Sample Lines"])
        val sectors = data.sectors.observeSummaries().first().associate { it.sector.name to it.companyCount }
        assertEquals(mapOf("Astillero" to 2, "Armador" to 1), sectors)
        assertEquals(6, data.contacts.observeCount().first())
        assertEquals(2, data.events.observeCount().first())
        assertEquals(3, data.companies.observeCount().first())
    }
}

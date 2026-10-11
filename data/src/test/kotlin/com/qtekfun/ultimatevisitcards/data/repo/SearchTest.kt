package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
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

/** FTS4 search over contacts: accents, prefixes, every indexed field and index maintenance. Synthetic data. */
@RunWith(AndroidJUnit4::class)
class SearchTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    private suspend fun search(query: String): List<String> = data.contacts.search(query).first().map { it.lastName }

    private suspend fun contact(first: String, last: String, block: ContactDraft.() -> ContactDraft = { this }): Contact =
        data.contacts.create(ContactDraft(firstName = first, lastName = last).block()).value()

    @Test
    fun `accents and case are ignored on both sides`() = runTest(dispatcher) {
        contact("José", "Muñoz")
        assertEquals(listOf("Muñoz"), search("munoz"))
        assertEquals(listOf("Muñoz"), search("MUÑOZ"))
        assertEquals(listOf("Muñoz"), search("jose"))
    }

    @Test
    fun `terms are prefixes`() = runTest(dispatcher) {
        contact("Min-jun", "Kim") { copy(jobTitle = "Engineering Manager") }
        assertEquals(listOf("Kim"), search("engin man"))
        assertEquals(emptyList(), search("enginx"))
    }

    @Test
    fun `the trade fair scenario combines event, sector and country`() = runTest(dispatcher) {
        val shipyard = data.sectors.create("Astillero").value()
        val owner = data.sectors.create("Armador").value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val korean = data.companies.create(CompanyDraft("Example Heavy Industries Co., Ltd.", shipyard.id, "South Korea")).value()
        val korean2 = data.companies.create(CompanyDraft("Sample Lines", owner.id, "South Korea")).value()
        val spanish = data.companies.create(CompanyDraft("Astilleros Ficticios S.A.", shipyard.id, "Spain")).value()
        val a = contact("Ji-ho", "Park") { copy(companyId = korean.id) }
        val b = contact("Seo-yeon", "Lee") { copy(companyId = korean2.id) }
        val c = contact("Lucía", "García") { copy(companyId = spanish.id) }
        listOf(a, b, c).forEach { data.contacts.addToEvent(it.id, smm.id).value() }

        assertEquals(listOf("Park"), search("smm 2026 astillero south korea"))
        assertEquals(listOf("García", "Park"), search("astill"))
        assertEquals(listOf("Lee", "Park"), search("korea"))
    }

    @Test
    fun `email, phone, notes and card text are searchable`() = runTest(dispatcher) {
        val contact = contact("Ana", "Ruiz") {
            copy(email = "ana.ruiz@example.org", mobile = "+34 600 12 34 56", notes = "Prefiere WhatsApp")
        }
        val card = data.cards.create(BusinessCardDraft("cards/x/front_original.jpg", "cards/x/front.jpg", capturedAt = 1)).value()
        data.cards.attachToContact(card.id, contact.id).value()
        data.cards.updateOcr(card.id, OcrStatus.REVIEWED, rawOcrText = "Departamento de Compras").value()

        assertEquals(listOf("Ruiz"), search("example.org"))
        assertEquals(listOf("Ruiz"), search("600123456"))
        assertEquals(listOf("Ruiz"), search("whatsapp"))
        assertEquals(listOf("Ruiz"), search("compras"))
    }

    @Test
    fun `the index follows renames of company, sector and event`() = runTest(dispatcher) {
        val sector = data.sectors.create("Logística").value()
        val company = data.companies.create(CompanyDraft("Old Name", sector.id)).value()
        val event = data.events.create(EventDraft("Navalia")).value()
        val contact = contact("Marta", "Vidal") { copy(companyId = company.id) }
        data.contacts.addToEvent(contact.id, event.id).value()

        data.companies.update(company.copy(name = "Nuevo Nombre")).value()
        data.sectors.rename(sector.id, "Transporte").value()
        data.events.update(event.copy(name = "Posidonia")).value()

        assertEquals(emptyList(), search("old"))
        assertEquals(listOf("Vidal"), search("nuevo transporte posidonia"))
        assertEquals(emptyList(), search("navalia"))
    }

    @Test
    fun `deleting linked entities updates the index`() = runTest(dispatcher) {
        val sector = data.sectors.create("Automatización").value()
        val company = data.companies.create(CompanyDraft("Robots Ficticios", sector.id)).value()
        contact("Pablo", "Sanz") { copy(companyId = company.id) }

        data.sectors.delete(sector.id).value()
        assertEquals(emptyList(), search("automatizacion"))
        data.companies.delete(company.id).value()
        assertEquals(emptyList(), search("robots"))
        assertEquals(listOf("Sanz"), search("pablo"))
    }

    @Test
    fun `a deleted contact is not found and a blank query finds nothing`() = runTest(dispatcher) {
        val contact = contact("Eva", "Torres")
        data.contacts.delete(contact.id).value()
        assertEquals(emptyList(), search("torres"))
        contact("Eva", "Blanco")
        assertEquals(emptyList(), search("  "))
    }

    @Test
    fun `rebuilding the index gives the same results`() = runTest(dispatcher) {
        contact("Luca", "Bianchi") { copy(jobTitle = "Responsabile Vendite") }
        data.db.searchDao().deleteAll()
        assertEquals(emptyList(), search("bianchi"))
        data.contacts.rebuildSearchIndex()
        assertEquals(listOf("Bianchi"), search("vendite"))
    }
}

package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Foreign keys as in docs/02-modelo-datos.md: CASCADE and SET NULL. */
@RunWith(AndroidJUnit4::class)
class CascadeTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    @Test
    fun `deleting a contact deletes its cards and event links`() = runTest(dispatcher) {
        val event = data.events.create(EventDraft("SMM 2026")).value()
        val contact = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        val card = data.cards.create(BusinessCardDraft("o.jpg", "p.jpg", eventId = event.id, capturedAt = 1)).value()
        data.cards.attachToContact(card.id, contact.id).value()

        data.contacts.delete(contact.id).value()

        assertNull(data.cards.get(card.id))
        assertEquals(emptyList(), data.db.contactDao().contactIdsOfEvent(event.id))
        assertEquals(0, data.db.searchDao().count())
    }

    @Test
    fun `deleting an event keeps contacts and cards`() = runTest(dispatcher) {
        val event = data.events.create(EventDraft("Navalia")).value()
        val contact = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        val card = data.cards.create(BusinessCardDraft("o.jpg", "p.jpg", eventId = event.id, capturedAt = 1)).value()
        data.cards.attachToContact(card.id, contact.id).value()

        data.events.delete(event.id).value()

        assertNotNull(data.contacts.get(contact.id))
        assertNull(data.cards.get(card.id)!!.eventId)
        assertEquals(emptySet(), data.contacts.eventIds(contact.id))
    }

    @Test
    fun `deleting a company or a sector unlinks instead of deleting`() = runTest(dispatcher) {
        val sector = data.sectors.create("Proveedor").value()
        val company = data.companies.create(CompanyDraft("Suministros Ficticios", sector.id)).value()
        val contact = data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = company.id)).value()

        data.sectors.delete(sector.id).value()
        assertNull(data.companies.get(company.id)!!.sectorId)

        data.companies.delete(company.id).value()
        assertNull(data.contacts.get(contact.id)!!.companyId)
    }
}

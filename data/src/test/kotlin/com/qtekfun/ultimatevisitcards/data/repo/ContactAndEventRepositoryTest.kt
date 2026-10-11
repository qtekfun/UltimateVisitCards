package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactAndEventRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    @Test
    fun `contacts are cleaned on save and listed newest first`() = runTest(dispatcher) {
        val first = data.contacts.create(ContactDraft(" Ana ", "Ruiz", email = " Ana@Example.ORG ", phone = " ")).value()
        assertEquals("Ana", first.firstName)
        assertEquals("ana@example.org", first.email)
        assertNull(first.phone)
        data.contacts.create(ContactDraft("Luis", "Gil")).value()
        assertEquals(listOf("Gil", "Ruiz"), data.contacts.observeAll().first().map { it.lastName })
    }

    @Test
    fun `updating a contact keeps createdAt and is observed`() = runTest(dispatcher) {
        val contact = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        data.contacts.observe(contact.id).test {
            assertEquals("Ruiz", awaitItem()?.lastName)
            data.contacts.update(contact.copy(lastName = "Ruiz Pérez", createdAt = 0)).value()
            val updated = awaitItem()!!
            assertEquals("Ruiz Pérez", updated.lastName)
            assertEquals(contact.createdAt, updated.createdAt)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.contacts.update(contact.copy(id = "nope")))
    }

    @Test
    fun `contacts by company and by event`() = runTest(dispatcher) {
        val company = data.companies.create(CompanyDraft("Ficticia S.L.")).value()
        val event = data.events.create(EventDraft("Navalia")).value()
        val a = data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = company.id)).value()
        val b = data.contacts.create(ContactDraft("Luis", "Gil")).value()
        data.contacts.addToEvent(a.id, event.id).value()
        data.contacts.addToEvent(b.id, event.id).value()
        // Adding twice is harmless.
        data.contacts.addToEvent(b.id, event.id).value()

        assertEquals(listOf("Ruiz"), data.contacts.observeByCompany(company.id).first().map { it.lastName })
        assertEquals(listOf("Gil", "Ruiz"), data.contacts.observeByEvent(event.id).first().map { it.lastName })

        data.contacts.removeFromEvent(b.id, event.id).value()
        assertEquals(listOf("Ruiz"), data.contacts.observeByEvent(event.id).first().map { it.lastName })
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.contacts.addToEvent(a.id, "nope"))
    }

    @Test
    fun `events show their contact count, most recent first`() = runTest(dispatcher) {
        val older = data.events.create(EventDraft("SMM 2024", startDate = 1_000)).value()
        val newer = data.events.create(EventDraft("SMM 2026", startDate = 2_000)).value()
        val contact = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        data.contacts.addToEvent(contact.id, newer.id).value()

        val summaries = data.events.observeAll().first()
        assertEquals(listOf(newer.id, older.id), summaries.map { it.event.id })
        assertEquals(listOf(1, 0), summaries.map { it.contactCount })
        assertEquals(DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME)), data.events.create(EventDraft(" ")))
    }
}

package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSide
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.usecase.AddCardToContactUseCase
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeContactsUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** docs/06 §4-6: merging keeps every piece of data, unites events and cards, one primary, and can be undone. */
@RunWith(AndroidJUnit4::class)
class MergeContactsTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val runner = RoomTransactionRunner(data.db)
    private val merge = MergeContactsUseCase(data.contacts, data.companies, data.cards, runner, data.clock)
    private val addCard = AddCardToContactUseCase(data.contacts, data.companies, data.cards, runner, data.clock)

    @After
    fun close() = data.close()

    private suspend fun side(contact: Contact) = MergeSide.of(
        contact,
        contact.companyId?.let { data.companies.get(it)?.name },
        data.cards.observeByContact(contact.id).first().maxOfOrNull { it.capturedAt } ?: 0
    )

    private suspend fun cardFor(contact: Contact, capturedAt: Long, eventId: String? = null): String {
        val card = data.cards.create(BusinessCardDraft("o$capturedAt", "p$capturedAt", eventId = eventId, capturedAt = capturedAt)).value()
        data.cards.attachToContact(card.id, contact.id).value()
        return card.id
    }

    @Test
    fun `merging keeps all data, unites events and cards, joins notes and deletes the other`() = runTest(dispatcher) {
        val abc = data.companies.create(CompanyDraft("ABC Marine")).value()
        val smm26 = data.events.create(EventDraft("SMM 2026")).value()
        val smm28 = data.events.create(EventDraft("SMM 2028")).value()
        val old = data.contacts.create(
            ContactDraft("John", "Smith", jobTitle = "Sales Manager", companyId = abc.id, phone = "+49 40 1", notes = "Met at SMM")
        ).value()
        val new = data.contacts.create(ContactDraft("John", "Smith", jobTitle = "Sales Director", email = "john@abc-demo.com", notes = "Wants a quote")).value()
        val oldCard = cardFor(old, 2026, smm26.id)
        val newCard = cardFor(new, 2028, smm28.id)

        val plan = MergePlan.of(side(old), side(new))
        merge(old.id, new.id, plan).value()

        val merged = data.contacts.get(old.id)!!
        assertNull(data.contacts.get(new.id))
        assertEquals("Sales Director", merged.jobTitle)
        assertEquals("+49 40 1", merged.phone)
        assertEquals("john@abc-demo.com", merged.email)
        assertEquals(abc.id, merged.companyId)
        assertTrue(merged.notes!!.startsWith("Met at SMM") && merged.notes!!.endsWith("Wants a quote"))
        assertEquals(setOf(smm26.id, smm28.id), data.contacts.eventIds(old.id))
        val cards = data.cards.observeByContact(old.id).first()
        assertEquals(listOf(newCard, oldCard), cards.map { it.id })
        assertEquals(listOf(true, false), cards.map { it.isPrimary })
        // Found by what each of them had.
        assertEquals(listOf(old.id), data.contacts.search("quote director").first().map { it.id })
    }

    @Test
    fun `undo puts both contacts back`() = runTest(dispatcher) {
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val a = data.contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Manager")).value()
        val b = data.contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Director")).value()
        val bCard = cardFor(b, 5, smm.id)
        val undo = merge(a.id, b.id, MergePlan.of(side(a), side(b))).value()

        merge.undo(undo).value()

        assertEquals("Manager", data.contacts.get(a.id)!!.jobTitle)
        assertEquals(b, data.contacts.get(b.id))
        assertEquals(listOf(bCard), data.cards.observeByContact(b.id).first().map { it.id })
        assertEquals(emptySet(), data.contacts.eventIds(a.id))
        assertEquals(setOf(smm.id), data.contacts.eventIds(b.id))
    }

    @Test
    fun `a contact cannot be merged with itself`() = runTest(dispatcher) {
        val a = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        assertTrue(merge(a.id, a.id, MergePlan.of(side(a), side(a))) is com.qtekfun.ultimatevisitcards.domain.model.DomainResult.Failure)
    }

    @Test
    fun `a new card for an existing contact - history, event, chosen values`() = runTest(dispatcher) {
        val smm28 = data.events.create(EventDraft("SMM 2028")).value()
        val john = data.contacts.create(ContactDraft("John", "Smith", jobTitle = "Sales Manager", phone = "+49 40 1")).value()
        val first = cardFor(john, 2026)
        val card = data.cards.create(BusinessCardDraft("o", "p", capturedAt = 2028)).value()
        data.cards.updateOcr(card.id, OcrStatus.PENDING_REVIEW, rawOcrText = "JOHN SMITH SALES DIRECTOR").value()
        val incoming = MergeSide(
            mapOf(
                MergeField.FIRST_NAME to "John",
                MergeField.LAST_NAME to "Smith",
                MergeField.JOB_TITLE to "Sales Director",
                MergeField.COMPANY to "ABC Marine GmbH"
            ),
            null,
            2028
        )
        // Keep the old phone, take the new title (default) and company.
        val plan = MergePlan.of(side(john), incoming).choose(MergeField.PHONE, MergeSource.EXISTING)

        addCard(card.id, john.id, plan, eventId = smm28.id, sectorId = null).value()

        val updated = data.contacts.get(john.id)!!
        assertEquals("Sales Director", updated.jobTitle)
        assertEquals("+49 40 1", updated.phone)
        assertEquals("ABC Marine GmbH", data.companies.get(updated.companyId!!)!!.name)
        assertEquals(listOf(card.id, first), data.cards.observeByContact(john.id).first().map { it.id })
        assertEquals(OcrStatus.REVIEWED, data.cards.get(card.id)!!.ocrStatus)
        assertEquals(setOf(smm28.id), data.contacts.eventIds(john.id))
        assertEquals(listOf(john.id), data.contacts.search("director").first().map { it.id })
    }

    @Test
    fun `marking a contact as added to the phone`() = runTest(dispatcher) {
        val a = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
        data.contacts.markExportedToPhone(a.id).value()
        assertTrue(data.contacts.get(a.id)!!.exportedToPhoneAt != null)
    }
}

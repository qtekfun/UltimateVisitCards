package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessCardRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    private fun draft(capturedAt: Long, eventId: String? = null) =
        BusinessCardDraft("cards/$capturedAt/front_original.jpg", "cards/$capturedAt/front.jpg", eventId = eventId, capturedAt = capturedAt)

    @Test
    fun `a captured card exists before its contact and counts as pending review`() = runTest(dispatcher) {
        data.cards.observeAwaitingReviewCount().test {
            assertEquals(0, awaitItem())
            val card = data.cards.create(draft(10)).value()
            assertEquals(1, awaitItem())
            assertEquals(OcrStatus.PENDING, data.cards.get(card.id)!!.ocrStatus)

            data.cards.updateOcr(card.id, OcrStatus.OCR_ERROR).value()
            // Still pending: a failed OCR is reviewed by hand, the photo is never lost.
            expectNoEvents()

            val contact = data.contacts.create(ContactDraft("Ana", "Ruiz")).value()
            data.cards.attachToContact(card.id, contact.id).value()
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the review queue is ordered by capture time and an explicit id is kept`() = runTest(dispatcher) {
        data.cards.create(draft(30)).value()
        data.cards.create(draft(10), id = "fixed-id").value()
        assertEquals(listOf(10L, 30L), data.cards.observeAwaitingReview().first().map { it.capturedAt })
        assertEquals("fixed-id", data.cards.observeAwaitingReview().first().first().id)
    }

    @Test
    fun `OCR results are stored without overwriting what is not given`() = runTest(dispatcher) {
        val card = data.cards.create(draft(1)).value()
        data.cards.updateOcr(card.id, OcrStatus.PENDING_REVIEW, "TEXT", "{}", "mlkit").value()
        data.cards.updateOcr(card.id, OcrStatus.REVIEWED).value()
        val stored = data.cards.get(card.id)!!
        assertEquals(OcrStatus.REVIEWED, stored.ocrStatus)
        assertEquals("TEXT", stored.rawOcrText)
        assertEquals("mlkit", stored.ocrEngine)
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.cards.updateOcr("nope", OcrStatus.REVIEWED))
    }

    @Test
    fun `attaching cards links events and keeps the newest as the only primary`() = runTest(dispatcher) {
        val smm2026 = data.events.create(EventDraft("SMM 2026")).value()
        val smm2028 = data.events.create(EventDraft("SMM 2028")).value()
        val contact = data.contacts.create(ContactDraft("John", "Smith")).value()
        val old = data.cards.create(draft(2026, smm2026.id)).value()
        val new = data.cards.create(draft(2028, smm2028.id)).value()

        data.cards.attachToContact(old.id, contact.id).value()
        data.cards.attachToContact(new.id, contact.id).value()

        assertEquals(setOf(smm2026.id, smm2028.id), data.contacts.eventIds(contact.id))
        val cards = data.cards.observeByContact(contact.id).first()
        assertEquals(listOf(new.id, old.id), cards.map { it.id })
        assertEquals(listOf(true, false), cards.map { it.isPrimary })

        data.cards.setPrimary(old.id).value()
        assertEquals(listOf(false, true), data.cards.observeByContact(contact.id).first().map { it.isPrimary })
    }

    @Test
    fun `the only card of a contact cannot be deleted, an old one can`() = runTest(dispatcher) {
        val contact = data.contacts.create(ContactDraft("John", "Smith")).value()
        val old = data.cards.create(draft(1)).value()
        data.cards.attachToContact(old.id, contact.id).value()
        assertEquals(
            DomainResult.Failure(DomainError.NotAllowed(DomainError.Rule.ONLY_CARD_OF_CONTACT)),
            data.cards.delete(old.id)
        )

        val new = data.cards.create(draft(2)).value()
        data.cards.attachToContact(new.id, contact.id).value()
        data.cards.delete(new.id).value()
        val remaining = data.cards.observeByContact(contact.id).first()
        assertEquals(listOf(old.id), remaining.map { it.id })
        assertEquals(true, remaining.single().isPrimary)
    }

    @Test
    fun `a card cannot point to a missing event or contact`() = runTest(dispatcher) {
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.cards.create(draft(1, "nope")))
        val card = data.cards.create(draft(1)).value()
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.cards.attachToContact(card.id, "nope"))
    }
}

package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.usecase.ReviewedCard
import com.qtekfun.ultimatevisitcards.domain.usecase.SaveReviewedCardUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveReviewedCardUseCaseTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val save = SaveReviewedCardUseCase(data.companies, data.contacts, data.cards, RoomTransactionRunner(data.db))

    @After
    fun close() = data.close()

    private suspend fun pendingCard(eventId: String? = null): String {
        val card = data.cards.create(BusinessCardDraft("cards/x/front_original.jpg", "cards/x/front.jpg", eventId = eventId, capturedAt = 1)).value()
        data.cards.updateOcr(card.id, OcrStatus.PENDING_REVIEW, rawOcrText = "ANA RUIZ NAVALTECH").value()
        return card.id
    }

    private fun reviewed(cardId: String, company: String? = "Navaltech S.L.", sectorId: String? = null, eventId: String? = null) = ReviewedCard(
        cardId = cardId,
        firstName = "Ana",
        lastName = "Ruiz",
        jobTitle = "Directora Comercial",
        companyName = company,
        sectorId = sectorId,
        country = "Spain",
        phone = null,
        mobile = "+34 600 111 222",
        email = "ana@navaltech-demo.es",
        website = "www.navaltech-demo.es",
        address = null,
        notes = null,
        eventId = eventId
    )

    @Test
    fun `a new company is created with the card's sector, country and website`() = runTest(dispatcher) {
        val sector = data.sectors.create("Astillero").value()
        val cardId = pendingCard()
        val contact = save(reviewed(cardId, sectorId = sector.id)).value()

        val company = data.companies.get(contact.companyId!!)!!
        assertEquals("Navaltech S.L.", company.name)
        assertEquals(sector.id, company.sectorId)
        assertEquals("Spain", company.country)
        assertEquals("www.navaltech-demo.es", company.website)
        val card = data.cards.get(cardId)!!
        assertEquals(contact.id, card.contactId)
        assertEquals(OcrStatus.REVIEWED, card.ocrStatus)
        assertTrue(card.isPrimary)
        assertEquals(0, data.cards.observeAwaitingReviewCount().first())
        // Searchable by the card text and the company.
        assertEquals(listOf(contact.id), data.contacts.search("navaltech astillero").first().map { it.id })
    }

    @Test
    fun `an existing company is linked by normalised name and its sector updated`() = runTest(dispatcher) {
        val existing = data.companies.create(CompanyDraft("NAVALTECH")).value()
        val sector = data.sectors.create("Electrónica").value()
        val contact = save(reviewed(pendingCard(), company = "Navaltech, S.L.", sectorId = sector.id)).value()
        assertEquals(existing.id, contact.companyId)
        assertEquals(sector.id, data.companies.get(existing.id)!!.sectorId)
        assertEquals(1, data.companies.observeAll().first().size)
    }

    @Test
    fun `the event chosen in the review goes to the card and the contact`() = runTest(dispatcher) {
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val cardId = pendingCard()
        val contact = save(reviewed(cardId, eventId = smm.id)).value()
        assertEquals(smm.id, data.cards.get(cardId)!!.eventId)
        assertEquals(setOf(smm.id), data.contacts.eventIds(contact.id))
    }

    @Test
    fun `without a company the contact is saved alone`() = runTest(dispatcher) {
        val contact = save(reviewed(pendingCard(), company = " ")).value()
        assertEquals(null, contact.companyId)
    }

    @Test
    fun `an empty review is refused`() = runTest(dispatcher) {
        val empty = reviewed(pendingCard(), company = null).copy(firstName = "", lastName = "", email = null, mobile = null)
        assertEquals(DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME)), save(empty))
    }

    @Test
    fun `a failure in any step rolls everything back`() = runTest(dispatcher) {
        val result = save(reviewed("missing-card"))
        assertEquals(DomainResult.Failure(DomainError.NotFound("missing-card")), result)
        assertEquals(emptyList(), data.contacts.observeAll().first())
        assertEquals(emptyList(), data.companies.observeAll().first())
        assertEquals(0, data.db.searchDao().count())
    }
}

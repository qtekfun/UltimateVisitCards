package com.qtekfun.ultimatevisitcards.domain.search

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ContactSearchDocumentTest {
    private val contact = Contact(
        id = "c1",
        firstName = "José",
        lastName = "Muñoz",
        jobTitle = "Director Comercial",
        email = "jose@example.com",
        phone = "+34 600 12 34 56",
        notes = "Interesado en sensores",
        createdAt = 0,
        updatedAt = 0
    )

    @Test
    fun `folds every field and joins related names`() {
        val doc = ContactSearchDocument.of(
            contact = contact,
            companyName = "Ejemplo Naval S.L.",
            companyCountry = "Spain",
            sectorName = "Electrónica",
            eventNames = listOf("SMM 2026", "Navalia"),
            ocrTexts = listOf("JOSÉ MUÑOZ", "Director")
        )
        assertEquals("jose munoz", doc.name)
        assertEquals("ejemplo naval s.l.", doc.company)
        assertEquals("electronica", doc.sector)
        assertEquals("smm 2026 navalia", doc.events)
        assertEquals("jose munoz director", doc.ocrText)
    }

    @Test
    fun `the contact's country wins over the company's`() {
        val own = ContactSearchDocument.of(contact.copy(country = "Portugal"), null, "Spain", null, emptyList(), emptyList())
        val inherited = ContactSearchDocument.of(contact, null, "South Korea", null, emptyList(), emptyList())
        assertEquals("portugal", own.country)
        assertEquals("south korea", inherited.country)
    }

    @Test
    fun `a phone is found by its groups, all its digits and its national number`() {
        val terms = ContactSearchDocument.phoneTerms(listOf("+34 600 12 34 56")).split(" ")
        assertTrue("600" in terms)
        assertTrue("34600123456" in terms)
        assertTrue("600123456" in terms)
    }

    @Test
    fun `00 international prefix is treated like plus`() {
        val terms = ContactSearchDocument.phoneTerms(listOf("0049 151 2345678")).split(" ")
        assertTrue("491512345678" in terms)
        assertTrue("1512345678" in terms)
    }

    @Test
    fun `national numbers are indexed as printed`() {
        val terms = ContactSearchDocument.phoneTerms(listOf("(040) 123-456")).split(" ")
        assertEquals(listOf("040", "123", "456", "040123456"), terms)
    }
}

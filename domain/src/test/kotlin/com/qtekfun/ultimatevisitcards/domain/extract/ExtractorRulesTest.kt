package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.ocr.NormRect
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrBlock
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrLine
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Focused rules of docs/04 §3, per language. Synthetic text only. */
class ExtractorRulesTest {
    private val extractor = CardFieldExtractor()

    /** One block per line, all the same height unless given as `height|text`. */
    private fun card(vararg lines: String, confidence: Float? = null): OcrResult {
        val blocks = lines.mapIndexed { i, raw ->
            val (height, text) = if ('|' in raw &&
                raw.substringBefore('|').toFloatOrNull() != null
            ) {
                raw.substringBefore('|').toFloat() to raw.substringAfter('|')
            } else {
                10f to raw
            }
            val box = NormRect(0.05f, 0.05f + i * 0.05f, 0.95f, 0.08f + i * 0.05f)
            OcrBlock(text, listOf(OcrLine(text, box, confidence, height)), box)
        }
        return OcrResult(lines.joinToString("\n"), blocks)
    }

    private fun read(vararg lines: String) = extractor.extract(card(*lines))

    @Test
    fun `mobile or landline by label in five languages`() {
        val cases = mapOf(
            "Móvil 600 111 222" to CardField.MOBILE,
            "Teléfono 600 111 222" to CardField.PHONE,
            "Mobile: 020 7946 0000" to CardField.MOBILE,
            "Portable 01 23 45 67 89" to CardField.MOBILE,
            "Handy 040 123456" to CardField.MOBILE,
            "Cellulare 02 1234 5678" to CardField.MOBILE,
            "Tel. 0171 2345678" to CardField.PHONE
        )
        cases.forEach { (line, field) -> assertTrue(read("Ana Ruiz", line)[field] != null, "$line → $field") }
    }

    @Test
    fun `mobile or landline by prefix without label`() {
        val mobiles = listOf("+34 612 345 678", "+49 151 23456789", "+33 6 12 34 56 78", "+39 347 123 4567", "+44 7700 900123")
        val landlines = listOf("+34 912 345 678", "+49 40 1234567", "+33 1 23 45 67 89", "+39 06 1234 5678", "+44 20 7946 0000")
        mobiles.forEach { assertTrue(read(it)[CardField.MOBILE] != null, "mobile $it") }
        landlines.forEach { assertTrue(read(it)[CardField.PHONE] != null, "landline $it") }
    }

    @Test
    fun `fax is never the phone and international formats are normalised`() {
        val fields = read("Fax +34 91 000 00 01", "0049 (0) 40 123 4567")
        assertEquals("+49 40 123 4567", fields.value(CardField.PHONE))
        assertNull(fields.value(CardField.MOBILE))
    }

    @Test
    fun `countries in five languages, codes and hints`() {
        assertEquals("South Korea", read("Busan, Corée du Sud").value(CardField.COUNTRY))
        assertEquals("Germany", read("Deutschland").value(CardField.COUNTRY))
        assertEquals("Italy", read("Genova, Italia").value(CardField.COUNTRY))
        assertEquals("Spain", read("ES").value(CardField.COUNTRY))
        assertEquals("Germany", read("Hafenweg 1, D-20457 Hamburg").value(CardField.COUNTRY))
        assertEquals("France", read("+33 1 23 45 67 89").value(CardField.COUNTRY))
        // +1 is shared by several countries: no guess.
        assertNull(read("+1 713 555 0100").value(CardField.COUNTRY))
    }

    @Test
    fun `addresses by postcode and street words`() {
        assertEquals("Calle Mayor 5, 28013 Madrid", read("Calle Mayor 5", "28013 Madrid").value(CardField.ADDRESS))
        assertEquals("Hafenstraße 3, 20457 Hamburg", read("Hafenstraße 3, 20457 Hamburg").value(CardField.ADDRESS))
        assertEquals("10 rue de la Paix, 75002 Paris", read("10 rue de la Paix, 75002 Paris").value(CardField.ADDRESS))
        assertEquals("Via Roma 1, 00184 Roma RM", read("Via Roma 1, 00184 Roma RM").value(CardField.ADDRESS))
        assertEquals("1 Canada Square, London E14 5AB", read("1 Canada Square, London E14 5AB").value(CardField.ADDRESS))
    }

    @Test
    fun `names are split with per-language rules`() {
        fun name(vararg lines: String) = read(*lines).let { it.value(CardField.FIRST_NAME) to it.value(CardField.LAST_NAME) }
        assertEquals("Juan" to "de la Cruz", name("Juan de la Cruz", "Calle Sol 1, 28001 Madrid, España"))
        assertEquals("María José" to "García", name("María José García", "España"))
        assertEquals("José" to "García López", name("José García López", "España"))
        assertEquals("Jean" to "DUPONT", name("Jean DUPONT"))
        assertEquals("Ludwig" to "van Beethoven", name("Ludwig van Beethoven"))
        assertEquals("Luc" to "MARTIN", name("MARTIN, Luc"))
        assertEquals("Anna" to "Weber", name("Dipl.-Ing. Anna Weber").also { assertEquals("Dipl.-Ing.", read("Dipl.-Ing. Anna Weber").value(CardField.NOTES)) })
    }

    @Test
    fun `nothing is invented`() {
        val fields = read("Ana Ruiz")
        assertEquals(setOf(CardField.FIRST_NAME, CardField.LAST_NAME), fields.fields.keys)
        assertEquals(ExtractedFields(), extractor.extract(OcrResult.EMPTY))
        // A public email domain is not the company's website.
        assertNull(read("ana.ruiz@gmail.com").value(CardField.WEBSITE))
    }

    @Test
    fun `lines nobody claims are kept`() {
        val fields = read("Ana Ruiz", "Stand B-412", "Hall 3")
        assertTrue(fields.unassigned.containsAll(listOf("Stand B-412", "Hall 3")))
    }

    @Test
    fun `low engine confidence makes fields doubtful`() {
        val sure = extractor.extract(card("Ana Ruiz", "ana@example-demo.com", confidence = 1f))
        val unsure = extractor.extract(card("Ana Ruiz", "ana@example-demo.com", confidence = 0.2f))
        assertTrue(sure.getValue(CardField.EMAIL).confidence > unsure.getValue(CardField.EMAIL).confidence)
        assertTrue(CardField.EMAIL !in sure.doubtful(ExtractedFields.DEFAULT_THRESHOLD))
        assertTrue(CardField.EMAIL in unsure.doubtful(ExtractedFields.DEFAULT_THRESHOLD))
    }

    @Test
    fun `the result survives a JSON round trip`() {
        val fields = read("NAVALTECH S.L.", "Ana Ruiz", "Directora", "ana@navaltech-demo.es")
        assertEquals(fields, ExtractedFields.fromJson(fields.toJson()))
        assertNull(ExtractedFields.fromJson("{broken"))
    }

    private fun ExtractedFields.getValue(field: CardField) = requireNotNull(this[field]) { "$field missing" }
}

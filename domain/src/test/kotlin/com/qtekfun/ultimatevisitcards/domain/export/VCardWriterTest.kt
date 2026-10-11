package com.qtekfun.ultimatevisitcards.domain.export

import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** vCard 3.0 output checked with a parser written here, independently of the writer. */
class VCardWriterTest {
    /** A parsed property: name, parameters, the raw (escaped) value and the unescaped one. */
    private data class Prop(val name: String, val params: String, val raw: String) {
        val value: String get() = unescape(raw)

        /** Components of a structured value (N, ADR), split on unescaped semicolons. */
        val components: List<String>
            get() {
                val parts = mutableListOf<String>()
                val sb = StringBuilder()
                var i = 0
                while (i < raw.length) {
                    when {
                        raw[i] == '\\' && i + 1 < raw.length -> {
                            sb.append(raw, i, i + 2)
                            i += 2
                        }
                        raw[i] == ';' -> {
                            parts += unescape(sb.toString())
                            sb.clear()
                            i++
                        }
                        else -> sb.append(raw[i++])
                    }
                }
                parts += unescape(sb.toString())
                return parts
            }
    }

    private companion object {
        fun unescape(value: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    val n = value[i + 1]
                    sb.append(if (n == 'n' || n == 'N') '\n' else n)
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
    }

    /** Minimal RFC 2426 reader: CRLF lines, unfolding, `name;params:value`. */
    private fun parse(text: String): List<List<Prop>> {
        assertTrue(text.endsWith("\r\n"), "lines must end with CRLF")
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(text), "bare LF found")
        val logical = mutableListOf<String>()
        for (line in text.split("\r\n").dropLast(1)) {
            if (line.startsWith(" ")) logical[logical.size - 1] = logical.last() + line.substring(1) else logical += line
        }
        val cards = mutableListOf<List<Prop>>()
        var current: MutableList<Prop>? = null
        for (line in logical) {
            val colon = line.indexOf(':')
            val head = line.substring(0, colon)
            val prop = Prop(head.substringBefore(';'), head.substringAfter(';', ""), line.substring(colon + 1))
            when (prop.name) {
                "BEGIN" -> current = mutableListOf()
                "END" -> cards += checkNotNull(current).toList()
                else -> checkNotNull(current) += prop
            }
        }
        return cards
    }

    private fun List<Prop>.find(name: String, params: String = "") = firstOrNull { it.name == name && it.params == params }

    private fun List<Prop>.value(name: String, params: String = "") = find(name, params)?.value

    private val full = VCardContact(
        firstName = "José María",
        lastName = "Núñez; López",
        jobTitle = "Director, Ventas",
        companyName = "Navaltech S.L.",
        phone = "+34 910 123 456",
        mobile = "+34 600 111 222",
        email = "jm@navaltech-demo.es",
        website = "www.navaltech-demo.es",
        address = "C/ Ficticia 12\n28001 Madrid",
        country = "Spain",
        notes = "Back\\slash, semicolon; and\nnew line",
    )

    @Test
    fun `every field is written and read back exactly`() {
        val card = parse(VCardWriter.toString(full)).single()
        assertEquals("3.0", card.value("VERSION"))
        assertEquals("José María Núñez; López", card.value("FN"))
        assertEquals("Navaltech S.L.", card.value("ORG"))
        assertEquals("Director, Ventas", card.value("TITLE"))
        assertEquals("+34 910 123 456", card.value("TEL", "TYPE=WORK,VOICE"))
        assertEquals("+34 600 111 222", card.value("TEL", "TYPE=CELL"))
        assertEquals("jm@navaltech-demo.es", card.value("EMAIL", "TYPE=INTERNET,WORK"))
        assertEquals("www.navaltech-demo.es", card.value("URL"))
        assertEquals("Back\\slash, semicolon; and\nnew line", card.value("NOTE"))
    }

    @Test
    fun `structured name and address keep their components`() {
        val card = parse(VCardWriter.toString(full)).single()
        assertEquals(listOf("Núñez; López", "José María", "", "", ""), checkNotNull(card.find("N")).components)
        val adr = checkNotNull(card.find("ADR", "TYPE=WORK")).components
        assertEquals(7, adr.size)
        assertEquals("C/ Ficticia 12\n28001 Madrid", adr[2])
        assertEquals("Spain", adr[6])
    }

    @Test
    fun `non-Latin text survives`() {
        val card = parse(VCardWriter.toString(VCardContact(firstName = "민준", lastName = "김", companyName = "Ελένη Ναυτιλιακή"))).single()
        assertEquals("민준 김", card.value("FN"))
        assertEquals("Ελένη Ναυτιλιακή", card.value("ORG"))
    }

    @Test
    fun `long lines are folded at 75 octets and unfold exactly`() {
        val notes = "Notas largas con acentos áéíóú y coreano 김민준 ".repeat(12).trim()
        val text = VCardWriter.toString(VCardContact(firstName = "Ana", notes = notes))
        text.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75, "line too long: $it") }
        assertTrue(text.contains("\r\n "), "expected folded lines")
        assertEquals(notes, parse(text).single().value("NOTE"))
    }

    @Test
    fun `several cards in one stream`() {
        val out = StringWriter()
        VCardWriter.write(listOf(VCardContact("Ana", "Ruiz"), VCardContact("Luis", "Gil"), VCardContact(companyName = "Solo Empresa")), out)
        val cards = parse(out.toString())
        assertEquals(listOf("Ana Ruiz", "Luis Gil", "Solo Empresa"), cards.map { it.value("FN") })
        assertEquals(3, Regex("BEGIN:VCARD").findAll(out.toString()).count())
    }

    @Test
    fun `empty fields are left out and FN falls back`() {
        val card = parse(VCardWriter.toString(VCardContact(email = "solo@example-demo.com", phone = " "))).single()
        assertEquals("solo@example-demo.com", card.value("FN"))
        assertNull(card.find("TEL", "TYPE=WORK,VOICE"))
        assertNull(card.find("ORG"))
        assertNull(card.find("ADR", "TYPE=WORK"))
        assertEquals(listOf("VERSION", "FN", "N", "EMAIL"), card.map { it.name })
    }
}

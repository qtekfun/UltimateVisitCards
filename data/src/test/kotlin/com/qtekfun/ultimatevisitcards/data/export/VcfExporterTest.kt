package com.qtekfun.ultimatevisitcards.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** `.vcf` export of everything, of a filter (an event) and of a selection. Synthetic contacts. */
@RunWith(AndroidJUnit4::class)
class VcfExporterTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val exporter = VcfExporter(data.db, dispatcher)

    @After
    fun close() = data.close()

    /** FN values, read independently of the writer: unfold CRLF continuations, then take FN lines. */
    private fun names(bytes: ByteArray): List<String> {
        val text = bytes.toString(Charsets.UTF_8).replace("\r\n ", "")
        assertEquals(text.split("BEGIN:VCARD").size - 1, text.split("END:VCARD").size - 1)
        return text.split("\r\n").filter { it.startsWith("FN:") }.map { it.removePrefix("FN:").replace("\\,", ",") }
    }

    @Test
    fun `exports everything, an event or a selection`() = runTest(dispatcher) {
        val company = data.companies.create(CompanyDraft("Navaltech S.L.", country = "Spain")).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val ana = data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = company.id, email = "ana@navaltech-demo.es")).value()
        val luis = data.contacts.create(ContactDraft("Luis", "Gil")).value()
        data.contacts.create(ContactDraft("Min-jun", "김"))
        data.contacts.addToEvent(ana.id, smm.id).value()

        val all = ByteArrayOutputStream()
        assertEquals(3, exporter.export(ContactFilter(), all))
        assertEquals(setOf("Ana Ruiz", "Luis Gil", "Min-jun 김"), names(all.toByteArray()).toSet())

        val event = ByteArrayOutputStream()
        assertEquals(1, exporter.export(ContactFilter(eventId = smm.id), event))
        val text = event.toByteArray().toString(Charsets.UTF_8)
        assertEquals(listOf("Ana Ruiz"), names(event.toByteArray()))
        assertTrue(text.contains("ORG:Navaltech S.L."))
        // The company's country goes into the address when the contact has none.
        assertTrue(text.contains("ADR;TYPE=WORK:;;;;;;Spain"))

        val selection = ByteArrayOutputStream()
        assertEquals(2, exporter.export(listOf(luis.id, ana.id, luis.id), selection))
        assertEquals(listOf("Luis Gil", "Ana Ruiz"), names(selection.toByteArray()))
    }

    @Test
    fun `an empty result writes nothing`() = runTest(dispatcher) {
        val out = ByteArrayOutputStream()
        assertEquals(0, exporter.export(ContactFilter(text = "nadie"), out))
        assertEquals(0, out.size())
    }
}

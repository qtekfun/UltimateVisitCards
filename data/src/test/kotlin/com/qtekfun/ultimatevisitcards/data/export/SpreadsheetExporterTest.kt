package com.qtekfun.ultimatevisitcards.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private val BOM = Char(0xFEFF).toString()

@RunWith(AndroidJUnit4::class)
class SpreadsheetExporterTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val exporter = SpreadsheetExporter(data.db, dispatcher)
    private val header = (1..SpreadsheetExporter.COLUMNS).map { "H$it" }

    @After
    fun close() = data.close()

    private suspend fun world() {
        val sector = data.sectors.create("Astillero").value()
        val company = data.companies.create(CompanyDraft("Example Heavy Industries", sector.id, "South Korea")).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val navalia = data.events.create(EventDraft("Navalia")).value()
        val park = data.contacts.create(
            ContactDraft("Ji-ho", "Park", jobTitle = "Sales Director", companyId = company.id, email = "park@example-demo.kr", notes = "=cmd")
        ).value()
        data.contacts.addToEvent(park.id, smm.id).value()
        data.contacts.addToEvent(park.id, navalia.id).value()
        data.contacts.create(ContactDraft("Ana", "Ruiz", country = "Spain")).value()
    }

    @Test
    fun `csv has the 14 columns with sector, inherited country, joined events and date`() = runTest(dispatcher) {
        world()
        val out = ByteArrayOutputStream()
        val count = exporter.export(ContactFilter(lastName = "park"), SpreadsheetFormat.CSV, header, { "D$it" }, out)
        assertEquals(1, count)
        val lines = String(out.toByteArray(), Charsets.UTF_8).removePrefix(BOM).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(header.joinToString(","), lines[0])
        val cells = lines[1].split(",")
        assertEquals(14, cells.size)
        assertEquals(listOf("Ji-ho", "Park", "Sales Director", "Example Heavy Industries", "Astillero", "South Korea"), cells.take(6))
        assertEquals("Navalia; SMM 2026", cells[11])
        assertTrue(cells[12].startsWith("D"))
        assertEquals("'=cmd", cells[13])
    }

    @Test
    fun `xlsx of everything`() = runTest(dispatcher) {
        world()
        val out = ByteArrayOutputStream()
        assertEquals(2, exporter.export(ContactFilter(), SpreadsheetFormat.XLSX, header, { "" }, out))
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> generateSequence { zip.nextEntry }.forEach { names += it.name } }
        assertTrue("xl/worksheets/sheet1.xml" in names)
    }
}

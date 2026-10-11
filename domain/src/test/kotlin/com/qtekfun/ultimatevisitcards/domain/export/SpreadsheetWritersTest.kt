package com.qtekfun.ultimatevisitcards.domain.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Element

private val BOM = Char(0xFEFF).toString()

/** CSV and XLSX read back with independent parsers (an RFC 4180 reader here, the JDK's XML parser). */
class SpreadsheetWritersTest {
    private val tricky = listOf(
        listOf("José", "Muñoz", "Director, Ventas", "ABC \"Marine\"", "Línea 1\nLínea 2"),
        listOf("=HYPERLINK(\"x\")", "+34 600", "-3", "@sum", "normal"),
        listOf("김민준", "Ελένη", "a;b", "", "tab\there")
    )
    private val table = Table(listOf("Nombre", "Apellidos", "Cargo", "Empresa", "Notas"), tricky)

    // --- CSV -------------------------------------------------------------------------------------------

    /** A plain RFC 4180 reader: quoted fields, doubled quotes, embedded commas and line breaks. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> field.append('"').also { i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> row += field.toString().also { field.clear() }
                c == '\r' && text.getOrNull(i + 1) == '\n' -> {
                    row += field.toString().also { field.clear() }
                    rows += row
                    row = mutableListOf()
                    i++
                }
                else -> field.append(c)
            }
            i++
        }
        return rows
    }

    @Test
    fun `csv - BOM, CRLF and every value back as written`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(table, out)
        val bytes = out.toByteArray()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        val rows = parseCsv(String(bytes, Charsets.UTF_8).removePrefix(BOM))
        assertEquals(table.header, rows[0])
        assertEquals(tricky[0], rows[1])
        assertEquals(tricky[2], rows[3])
    }

    @Test
    fun `csv - formulas are neutralised`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(table, out)
        val row = parseCsv(String(out.toByteArray(), Charsets.UTF_8).removePrefix(BOM))[2]
        assertEquals(listOf("'=HYPERLINK(\"x\")", "'+34 600", "'-3", "'@sum", "normal"), row)
    }

    // --- XLSX ------------------------------------------------------------------------------------------

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> files[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        return files
    }

    private fun xml(text: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray()))

    /** Cells by reference («A1» → text), read with the JDK's DOM parser. */
    private fun cells(sheet: String): Map<String, Pair<String, String?>> {
        val doc = xml(sheet)
        val list = doc.getElementsByTagNameNS("*", "c")
        return (0 until list.length).associate { i ->
            val c = list.item(i) as Element
            c.getAttribute("r") to (c.textContent to c.getAttribute("s").ifEmpty { null })
        }
    }

    @Test
    fun `xlsx - a valid package with the expected parts`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(table, out)
        val files = unzip(out.toByteArray())
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"),
            files.keys
        )
        // Every part is well-formed XML.
        files.values.forEach { xml(it) }
    }

    @Test
    fun `xlsx - cells, bold header, frozen row, widths and protections`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(table, out)
        val sheet = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        val cells = cells(sheet)
        assertEquals("Nombre" to "1", cells["A1"])
        assertEquals("ABC \"Marine\"", cells["D2"]?.first)
        assertEquals("Línea 1\nLínea 2", cells["E2"]?.first)
        assertEquals(null, cells["A2"]?.second)
        assertEquals("'=HYPERLINK(\"x\")", cells["A3"]?.first)
        assertEquals("김민준", cells["A4"]?.first)
        assertTrue(sheet.contains("""state="frozen""""))
        assertTrue(sheet.contains("<cols>"))
    }

    @Test
    fun `xlsx - column names and forbidden XML characters`() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA"), listOf(0, 25, 26, 51, 52).map { XlsxWriter.column(it) })
        assertEquals("a&amp;b&lt;c&gt;dok", XlsxWriter.xml("a&b<c>d\u0001ok"))
    }
}

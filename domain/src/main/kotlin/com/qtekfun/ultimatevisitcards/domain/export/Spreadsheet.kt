package com.qtekfun.ultimatevisitcards.domain.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A table to export (docs/05 §6): a header and rows of text cells, already formatted by the caller (dates
 * in the user's locale, several events joined with «; »).
 */
data class Table(val header: List<String>, val rows: List<List<String>>)

/** Spreadsheet programs run cells starting with these as formulas: prefix them with «'» (CSV injection). */
internal object FormulaGuard {
    private val triggers = setOf('=', '+', '-', '@', '\t', '\r')

    fun protect(value: String): String = if (value.isNotEmpty() && value[0] in triggers) "'$value" else value
}

/** CSV (RFC 4180): UTF-8 with BOM so Excel reads accents, comma separator, CRLF, quotes doubled. */
object CsvWriter {
    private val BOM = Char(0xFEFF)

    fun write(table: Table, out: OutputStream) {
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        writer.write(BOM.code)
        (listOf(table.header) + table.rows).forEach { row ->
            writer.write(row.joinToString(",") { quote(FormulaGuard.protect(it)) })
            writer.write("\r\n")
        }
        writer.flush()
    }

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/**
 * Minimal XLSX writer (no Apache POI, docs/README): one sheet, every cell an inline string, bold header,
 * frozen first row and column widths from the content. Valid Office Open XML that Excel, LibreOffice and
 * Google Sheets open.
 */
object XlsxWriter {
    private const val MAX_WIDTH = 60
    private const val MIN_WIDTH = 8

    fun write(table: Table, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", WORKBOOK)
        entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        entry("xl/styles.xml", STYLES)
        entry("xl/worksheets/sheet1.xml", sheet(table))
        zip.finish()
        zip.flush()
    }

    private fun sheet(table: Table): String = buildString {
        val all = listOf(table.header) + table.rows
        val columns = all.maxOfOrNull { it.size } ?: 0
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // Frozen header row.
        append(
            """<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>"""
        )
        if (columns > 0) {
            append("<cols>")
            for (c in 0 until columns) {
                val longest = all.maxOf { row -> row.getOrNull(c)?.lines()?.maxOfOrNull { it.length } ?: 0 }
                val width = (longest + 2).coerceIn(MIN_WIDTH, MAX_WIDTH)
                append("""<col min="${c + 1}" max="${c + 1}" width="$width" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        all.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                val style = if (r == 0) """ s="1"""" else ""
                append("""<c r="${column(c)}${r + 1}" t="inlineStr"$style><is><t xml:space="preserve">""")
                append(xml(FormulaGuard.protect(value)))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    internal fun column(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            name.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    /** XML-escaped, without the control characters XML 1.0 forbids. */
    internal fun xml(value: String): String = buildString {
        for (ch in value) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                else -> append(ch)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        """</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        """</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
        """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="Contacts" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
        """</Relationships>"""

    /** Style 0: normal; style 1: bold (the header). */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
        """</styleSheet>"""
}

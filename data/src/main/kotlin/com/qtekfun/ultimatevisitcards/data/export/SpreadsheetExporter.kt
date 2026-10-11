package com.qtekfun.ultimatevisitcards.data.export

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.ContactQueryBuilder
import com.qtekfun.ultimatevisitcards.domain.export.CsvWriter
import com.qtekfun.ultimatevisitcards.domain.export.Table
import com.qtekfun.ultimatevisitcards.domain.export.XlsxWriter
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

enum class SpreadsheetFormat { CSV, XLSX }

/**
 * CSV / XLSX export of the contacts matching a filter (docs/05 §6). Columns, in this order:
 * name, surname, job title, company, sector, country, phone, mobile, email, website, address, event(s),
 * date, notes. The header comes localised from the caller; several events are joined with «; ».
 */
class SpreadsheetExporter @Inject constructor(private val db: AppDatabase, @IoDispatcher private val io: CoroutineDispatcher) {
    /** Writes the table to [out] (left open) and returns the number of contacts. */
    suspend fun export(filter: ContactFilter, format: SpreadsheetFormat, header: List<String>, formatDate: (Long) -> String, out: OutputStream): Int =
        withContext(io) {
            val dao = db.contactDao()
            val rows = dao.list(ContactQueryBuilder.build(filter))
            val events = dao.allEventNames().groupBy({ it.contactId }, { it.name })
            val sectors = dao.companySectorNames().associate { it.companyId to it.sectorName }
            val table = Table(
                header = header,
                rows = rows.map { row ->
                    val c = row.contact
                    listOf(
                        c.firstName,
                        c.lastName,
                        c.jobTitle.orEmpty(),
                        row.companyName.orEmpty(),
                        c.companyId?.let { sectors[it] }.orEmpty(),
                        row.effectiveCountry.orEmpty(),
                        c.phone.orEmpty(),
                        c.mobile.orEmpty(),
                        c.email.orEmpty(),
                        c.website.orEmpty(),
                        c.address.orEmpty(),
                        events[c.id].orEmpty().joinToString("; "),
                        formatDate(c.createdAt),
                        c.notes.orEmpty()
                    )
                }
            )
            when (format) {
                SpreadsheetFormat.CSV -> CsvWriter.write(table, out)
                SpreadsheetFormat.XLSX -> XlsxWriter.write(table, out)
            }
            rows.size
        }

    companion object {
        const val COLUMNS = 14
    }
}

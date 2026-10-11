package com.qtekfun.ultimatevisitcards.data.export

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.ContactQueryBuilder
import com.qtekfun.ultimatevisitcards.data.db.dao.ContactListRow
import com.qtekfun.ultimatevisitcards.domain.export.VCardContact
import com.qtekfun.ultimatevisitcards.domain.export.VCardWriter
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * `.vcf` export (docs/05 §4): one contact, a selection, everything, or whatever a [ContactFilter] selects
 * (event, company, sector… the same filters as the list). Writes UTF-8 to a stream the caller opened (SAF
 * or a FileProvider file) and leaves it open. Returns the number of contacts written.
 */
class VcfExporter @Inject constructor(private val db: AppDatabase, @IoDispatcher private val io: CoroutineDispatcher) {
    suspend fun export(filter: ContactFilter, out: OutputStream): Int {
        val rows = db.contactDao().list(ContactQueryBuilder.build(filter))
        return write(rows, out)
    }

    suspend fun export(contactIds: List<String>, out: OutputStream): Int {
        val rows = contactIds.distinct().chunked(ContactQueryBuilder.MAX_IDS).flatMap { db.contactDao().list(ContactQueryBuilder.byIds(it)) }
        return write(rows, out)
    }

    private suspend fun write(rows: List<ContactListRow>, out: OutputStream): Int = withContext(io) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        VCardWriter.write(rows.map { it.toVCard() }, writer)
        rows.size
    }

    companion object {
        fun ContactListRow.toVCard() = VCardContact(
            firstName = contact.firstName,
            lastName = contact.lastName,
            jobTitle = contact.jobTitle,
            companyName = companyName,
            phone = contact.phone,
            mobile = contact.mobile,
            email = contact.email,
            website = contact.website,
            address = contact.address,
            country = effectiveCountry,
            notes = contact.notes
        )
    }
}

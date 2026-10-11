package com.qtekfun.ultimatevisitcards.data.repo

import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.toDomain
import com.qtekfun.ultimatevisitcards.data.db.toEntity
import com.qtekfun.ultimatevisitcards.domain.search.ContactSearchDocument

/**
 * Keeps `contact_fts` in step with the data: a contact's row is rebuilt whenever the contact, its company,
 * its sector, its events or its cards change.
 *
 * Every function must run inside the write transaction of the change that triggered it, so the index never
 * disagrees with the data.
 */
class SearchIndexer(private val db: AppDatabase) {
    suspend fun reindex(contactId: String) {
        val searchDao = db.searchDao()
        searchDao.delete(contactId)
        val contact = db.contactDao().get(contactId)?.toDomain() ?: return
        val company = contact.companyId?.let { db.companyDao().get(it) }
        val sector = company?.sectorId?.let { db.sectorDao().get(it) }
        val document = ContactSearchDocument.of(
            contact = contact,
            companyName = company?.name,
            companyCountry = company?.country,
            sectorName = sector?.name,
            eventNames = db.contactDao().eventNames(contactId),
            ocrTexts = db.businessCardDao().ocrTexts(contactId)
        )
        searchDao.insert(document.toEntity())
    }

    suspend fun reindex(contactIds: Collection<String>) {
        contactIds.distinct().forEach { reindex(it) }
    }

    /** Rebuilds the whole index (after a restore, or «Reindex» in Settings). */
    suspend fun rebuild() {
        db.searchDao().deleteAll()
        reindex(db.contactDao().allIds())
    }
}

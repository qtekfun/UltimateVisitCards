package com.qtekfun.ultimatevisitcards.data.db.entity

import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.FtsOptions

/**
 * Standalone FTS4 index (not `contentEntity`: it mixes data from several tables). One row per contact,
 * rebuilt by `SearchIndexer` in the same transaction as any change that affects it.
 *
 * The text is folded by the app before indexing (`TextNormalizer`), so the default `unicode61` tokenizer is
 * enough on every SQLite version (see docs/decisions.md).
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["contactId"])
@Entity(tableName = "contact_fts")
data class ContactFtsEntity(
    val contactId: String,
    val name: String,
    val company: String,
    val jobTitle: String,
    val country: String,
    val sector: String,
    val email: String,
    val phone: String,
    val notes: String,
    val ocrText: String,
    val events: String
)

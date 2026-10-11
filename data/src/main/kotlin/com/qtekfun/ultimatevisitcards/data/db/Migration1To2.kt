package com.qtekfun.ultimatevisitcards.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v2: the search index gets `firstName` and `lastName` columns for the name filters. An FTS4 table cannot
 * be altered, so it is recreated empty; `SearchIndexHealth` rebuilds it on the next start (the index is
 * derived data, nothing is lost).
 */
object Migration1To2 : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `contact_fts`")
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `contact_fts` USING FTS4(`contactId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `company` TEXT NOT NULL, `jobTitle` TEXT NOT NULL, " +
                "`country` TEXT NOT NULL, `sector` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `ocrText` TEXT NOT NULL, `events` TEXT NOT NULL, tokenize=unicode61, notindexed=`contactId`)"
        )
    }
}

package com.qtekfun.ultimatevisitcards.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.qtekfun.ultimatevisitcards.data.db.dao.BusinessCardDao
import com.qtekfun.ultimatevisitcards.data.db.dao.CompanyDao
import com.qtekfun.ultimatevisitcards.data.db.dao.ContactDao
import com.qtekfun.ultimatevisitcards.data.db.dao.EventDao
import com.qtekfun.ultimatevisitcards.data.db.dao.ExportDao
import com.qtekfun.ultimatevisitcards.data.db.dao.SearchDao
import com.qtekfun.ultimatevisitcards.data.db.dao.SectorDao
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactFtsEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.EventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity

@Database(
    entities = [
        SectorEntity::class,
        CompanyEntity::class,
        ContactEntity::class,
        EventEntity::class,
        ContactEventEntity::class,
        BusinessCardEntity::class,
        ContactFtsEntity::class
    ],
    version = AppDatabase.VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sectorDao(): SectorDao

    abstract fun companyDao(): CompanyDao

    abstract fun contactDao(): ContactDao

    abstract fun eventDao(): EventDao

    abstract fun businessCardDao(): BusinessCardDao

    abstract fun searchDao(): SearchDao

    abstract fun exportDao(): ExportDao

    companion object {
        const val VERSION = 2
        const val NAME = "ultimatevisitcards.db"

        /** Every migration, in order. Each one has its test (docs/02-modelo-datos.md). */
        val MIGRATIONS: Array<androidx.room3.migration.Migration> = arrayOf(Migration1To2)
    }
}

package com.qtekfun.ultimatevisitcards.data.backup

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.EventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream

enum class RestoreMode {
    /** Adds what is missing and updates only what the backup has newer (default). */
    MERGE,

    /** Deletes everything first (strong confirmation in the UI). */
    REPLACE,
}

/** What a backup holds, or why it cannot be restored. */
sealed interface InspectResult {
    data class Ready(val manifest: BackupManifest, val counts: BackupCounts) : InspectResult

    /** Encrypted: ask for the password. */
    data object NeedsPassword : InspectResult

    /** Encrypted and the password does not open it (not the same as a damaged file). */
    data object WrongPassword : InspectResult

    /** Written by a newer version of the app. */
    data class TooNew(val version: Int) : InspectResult

    /** Not a ZIP, or a ZIP that is not a backup of this app. */
    data object NotABackup : InspectResult

    /** A backup, but damaged or unsafe (truncated, bad JSON, missing image, path outside the archive…). */
    data class Corrupt(val reason: String) : InspectResult
}

data class RestoreCounts(
    val sectors: Int = 0,
    val companies: Int = 0,
    val events: Int = 0,
    val contacts: Int = 0,
    val contactEvents: Int = 0,
    val cards: Int = 0,
)

/** The final summary (docs/05 §3.7: added, updated, skipped), the images copied and the backed-up settings. */
data class RestoreReport(
    val added: RestoreCounts,
    val updated: RestoreCounts,
    val skipped: RestoreCounts,
    val images: Int,
    val settings: BackupSettings?,
)

sealed interface RestoreOutcome {
    data class Done(val report: RestoreReport) : RestoreOutcome

    /** The archive itself cannot be restored ([reason] says why); nothing was touched. */
    data class Rejected(val reason: InspectResult) : RestoreOutcome

    /** Something failed while restoring; the database and the images are as they were before. */
    data class Failed(val reason: String) : RestoreOutcome
}

/**
 * Restores a backup made by `BackupExporter` (docs/05 §3), from a stream that can be opened more than once
 * (a SAF document): one pass validates and reads the data, a second one copies the images.
 *
 * The database part runs in a single write transaction (sectors → companies → events → contacts → links →
 * cards, then the search index). Images are copied to a staging folder inside that transaction and moved
 * into place only after it committed: any failure or cancellation rolls the database back and deletes the
 * staged files, so a failed restore changes nothing. IDs are kept; in [RestoreMode.MERGE] the same UUID is
 * the same row, and companies (normalised name) and sectors (folded name) that already exist with another id
 * are linked instead of duplicated. Restoring the same backup twice changes nothing the second time.
 */
class BackupRestorer @Inject constructor(
    private val db: AppDatabase,
    private val store: ImageStore,
    private val indexer: SearchIndexer,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val companyNames by lazy { CompanyNameNormalizer.fromBundledDictionary() }

    /** Validates the backup without touching anything (docs/05 §3, steps 1-3). */
    suspend fun inspect(open: () -> InputStream, password: CharArray?): InspectResult = withContext(io) {
        when (val read = read(open, password)) {
            is Read.Ok -> InspectResult.Ready(read.archive.manifest, read.archive.counts())
            is Read.Rejected -> read.reason
        }
    }

    suspend fun restore(
        open: () -> InputStream,
        password: CharArray?,
        mode: RestoreMode,
        onProgress: (Float) -> Unit = {},
    ): RestoreOutcome = withContext(io) {
        val archive = when (val read = read(open, password)) {
            is Read.Ok -> read.archive
            is Read.Rejected -> return@withContext RestoreOutcome.Rejected(read.reason)
        }
        onProgress(PROGRESS_READ)
        val staging = store.newStaging()
        var committed = false
        try {
            val result = db.withWriteTransaction {
                if (mode == RestoreMode.REPLACE) clearAll()
                val imported = import(archive.database)
                onProgress(PROGRESS_DATABASE)
                val wanted = imported.images.filter { BackupFormat.entryFor(it) in archive.images }.toSet()
                stage(open, password, wanted, staging) { onProgress(PROGRESS_DATABASE + (PROGRESS_IMAGES - PROGRESS_DATABASE) * it) }
                fixPrimaryCards(imported.contacts)
                indexer.rebuild()
                imported
            }
            committed = true
            val copied = store.commitStaging(staging)
            // «Replace everything»: the images of cards that are no longer in the database go too.
            if (mode == RestoreMode.REPLACE) store.deleteOrphans(db.businessCardDao().all().map { it.id }.toSet())
            onProgress(1f)
            RestoreOutcome.Done(RestoreReport(result.added.build(), result.updated.build(), result.skipped.build(), copied, archive.settings))
        } catch (e: IOException) {
            RestoreOutcome.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SQLiteException) {
            RestoreOutcome.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            if (!committed) withContext(NonCancellable) { store.discardStaging(staging) }
        }
    }

    // --- Reading and validation ------------------------------------------------------------------------

    private class Archive(val manifest: BackupManifest, val database: BackupDatabase, val settings: BackupSettings?, val images: Set<String>) {
        fun counts() = BackupCounts(
            contacts = database.contacts.size,
            companies = database.companies.size,
            sectors = database.sectors.size,
            events = database.events.size,
            contactEvents = database.contactEvents.size,
            businessCards = database.businessCards.size,
            images = images.size,
        )
    }

    private sealed interface Read {
        class Ok(val archive: Archive) : Read

        class Rejected(val reason: InspectResult) : Read
    }

    private fun zip(input: InputStream, password: CharArray?) = if (password == null) ZipInputStream(input) else ZipInputStream(input, password)

    private fun read(open: () -> InputStream, password: CharArray?): Read {
        var manifestText: String? = null
        var databaseText: String? = null
        var settingsText: String? = null
        val images = mutableSetOf<String>()
        var foreign: String? = null
        var entries = 0
        try {
            open().use { raw ->
                zip(raw, password).use { zip ->
                    while (true) {
                        val header = zip.nextEntry ?: break
                        entries++
                        val name = header.fileName
                        if (!isSafe(name)) return Read.Rejected(InspectResult.Corrupt("Unsafe entry: $name"))
                        if (header.isEncrypted && password == null) return Read.Rejected(InspectResult.NeedsPassword)
                        when {
                            !ALLOWED_ENTRY.matches(name) -> foreign = name
                            header.isDirectory -> Unit
                            name == BackupFormat.MANIFEST -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                            name == BackupFormat.DATABASE -> databaseText = zip.readBytes().toString(Charsets.UTF_8)
                            name == BackupFormat.SETTINGS -> settingsText = zip.readBytes().toString(Charsets.UTF_8)
                            else -> {
                                // Read through: checks the CRC (and the decryption) of every image.
                                drain(zip)
                                images += name
                            }
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            return Read.Rejected(classify(e, password, entries))
        } catch (e: IOException) {
            return Read.Rejected(if (entries == 0) InspectResult.NotABackup else InspectResult.Corrupt(e.message ?: "Unreadable archive"))
        }
        // Any other ZIP is «not a backup»; a backup with unexpected content is damaged.
        val manifestJson = manifestText ?: return Read.Rejected(if (databaseText == null) InspectResult.NotABackup else InspectResult.Corrupt("No manifest"))
        foreign?.let { return Read.Rejected(InspectResult.Corrupt("Unexpected entry: $it")) }
        val manifest = try {
            BackupFormat.json.decodeFromString(BackupManifest.serializer(), manifestJson)
        } catch (e: SerializationException) {
            return Read.Rejected(InspectResult.Corrupt("Unreadable manifest"))
        } catch (e: IllegalArgumentException) {
            return Read.Rejected(InspectResult.Corrupt("Unreadable manifest"))
        }
        if (manifest.backupVersion > BackupFormat.VERSION) return Read.Rejected(InspectResult.TooNew(manifest.backupVersion))
        if (manifest.backupVersion < 1) return Read.Rejected(InspectResult.Corrupt("Unknown backup version ${manifest.backupVersion}"))
        val databaseJson = databaseText ?: return Read.Rejected(InspectResult.Corrupt("No database"))
        val database = try {
            val element = BackupFormat.json.parseToJsonElement(databaseJson).jsonObject
            BackupFormat.json.decodeFromJsonElement(BackupDatabase.serializer(), BackupMigrations.upgrade(manifest.backupVersion, element))
        } catch (e: SerializationException) {
            return Read.Rejected(InspectResult.Corrupt("Unreadable database"))
        } catch (e: IllegalArgumentException) {
            return Read.Rejected(InspectResult.Corrupt("Unreadable database"))
        } catch (e: IllegalStateException) {
            return Read.Rejected(InspectResult.Corrupt(e.message ?: "Unreadable database"))
        }
        val settings = settingsText?.let {
            try {
                BackupFormat.json.decodeFromString(BackupSettings.serializer(), it)
            } catch (e: SerializationException) {
                null // Preferences are not essential: the data is restored anyway.
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        val archive = Archive(manifest, database, settings, images)
        problems(archive)?.let { return Read.Rejected(InspectResult.Corrupt(it)) }
        return Read.Ok(archive)
    }

    private fun drain(input: InputStream) {
        val buffer = ByteArray(BUFFER)
        while (input.read(buffer) != -1) Unit
    }

    private fun classify(e: ZipException, password: CharArray?, entries: Int): InspectResult = when {
        e.type == ZipException.Type.WRONG_PASSWORD -> if (password == null) InspectResult.NeedsPassword else InspectResult.WrongPassword
        password == null && e.message?.contains("password", ignoreCase = true) == true -> InspectResult.NeedsPassword
        entries == 0 -> InspectResult.NotABackup
        else -> InspectResult.Corrupt(e.message ?: "Damaged archive")
    }

    /** Zip slip: no backslashes, no absolute paths, no `..` segments (whatever the rest of the name). */
    private fun isSafe(name: String): Boolean = !name.contains('\\') && !name.startsWith("/") && name.split('/').none { it == ".." }

    /** A reason why the data cannot be restored as it is, or null when it is consistent. */
    private fun problems(archive: Archive): String? {
        val d = archive.database
        fun duplicated(ids: List<String>) = ids.size != ids.toSet().size
        if (duplicated(d.sectors.map { it.id }) || duplicated(d.companies.map { it.id }) || duplicated(d.events.map { it.id }) ||
            duplicated(d.contacts.map { it.id }) || duplicated(d.businessCards.map { it.id })
        ) {
            return "Duplicated ids"
        }
        val sectors = d.sectors.map { it.id }.toSet()
        val companies = d.companies.map { it.id }.toSet()
        val events = d.events.map { it.id }.toSet()
        val contacts = d.contacts.map { it.id }.toSet()
        if (d.companies.any { it.sectorId != null && it.sectorId !in sectors }) return "Company with unknown sector"
        if (d.contacts.any { it.companyId != null && it.companyId !in companies }) return "Contact with unknown company"
        if (d.contactEvents.any { it.contactId !in contacts || it.eventId !in events }) return "Event link with unknown contact or event"
        if (d.businessCards.any { (it.contactId != null && it.contactId !in contacts) || (it.eventId != null && it.eventId !in events) }) {
            return "Card with unknown contact or event"
        }
        for (card in d.businessCards) {
            val required = listOfNotNull(card.frontProcessed, card.backProcessed) +
                if (archive.manifest.includesOriginals) listOfNotNull(card.frontOriginal, card.backOriginal) else emptyList()
            val all = listOfNotNull(card.frontOriginal, card.frontProcessed, card.backOriginal, card.backProcessed)
            // Every path must be one of this card's image paths: nothing can be written elsewhere.
            if (all.any { !ImageStore.IMAGE_PATH.matches(it) || !it.startsWith("cards/${card.id}/") }) return "Card ${card.id} with an invalid image path"
            required.firstOrNull { BackupFormat.entryFor(it) !in archive.images }?.let { return "Missing image $it" }
        }
        return null
    }

    // --- Import ----------------------------------------------------------------------------------------

    private class Tally {
        var sectors = 0
        var companies = 0
        var events = 0
        var contacts = 0
        var contactEvents = 0
        var cards = 0

        fun build() = RestoreCounts(sectors, companies, events, contacts, contactEvents, cards)
    }

    private class Imported(val added: Tally, val updated: Tally, val skipped: Tally, val images: Set<String>, val contacts: Set<String>)

    private suspend fun clearAll() {
        db.searchDao().deleteAll()
        db.businessCardDao().deleteAll()
        db.contactDao().deleteAllLinks()
        db.contactDao().deleteAll()
        db.eventDao().deleteAll()
        db.companyDao().deleteAll()
        db.sectorDao().deleteAll()
    }

    private suspend fun import(d: BackupDatabase): Imported {
        val added = Tally()
        val updated = Tally()
        val skipped = Tally()
        val images = mutableSetOf<String>()
        val touchedContacts = mutableSetOf<String>()

        // Sectors: same id → kept; same folded name with another id → linked.
        val sectorIds = mutableMapOf<String, String>()
        for (s in d.sectors) {
            val normalized = TextNormalizer.fold(s.name)
            val existing = db.sectorDao().get(s.id) ?: db.sectorDao().findByNormalizedName(normalized)
            if (existing != null) {
                sectorIds[s.id] = existing.id
                skipped.sectors++
            } else {
                db.sectorDao().insert(SectorEntity(s.id, s.name, normalized, s.sortOrder, s.createdAt))
                sectorIds[s.id] = s.id
                added.sectors++
            }
        }

        // Companies: same id → updated if the backup is newer; same normalised name with another id → linked.
        val companyIds = mutableMapOf<String, String>()
        for (c in d.companies) {
            val normalized = companyNames.normalize(c.name)
            val entity = CompanyEntity(c.id, c.name, normalized, c.sectorId?.let { sectorIds[it] }, c.country, c.website, c.notes, c.createdAt, c.updatedAt)
            val existing = db.companyDao().get(c.id)
            if (existing != null) {
                companyIds[c.id] = c.id
                val clash = db.companyDao().findByNormalizedName(normalized)
                if (c.updatedAt > existing.updatedAt && (clash == null || clash.id == c.id)) {
                    db.companyDao().update(entity)
                    updated.companies++
                } else {
                    skipped.companies++
                }
                continue
            }
            val sameName = db.companyDao().findByNormalizedName(normalized)
            if (sameName != null) {
                companyIds[c.id] = sameName.id
                skipped.companies++
            } else {
                db.companyDao().insert(entity)
                companyIds[c.id] = c.id
                added.companies++
            }
        }

        // Events have no update date: an existing one is kept.
        for (e in d.events) {
            if (db.eventDao().get(e.id) != null) {
                skipped.events++
            } else {
                db.eventDao().insert(EventEntity(e.id, e.name, e.location, e.startDate, e.endDate, e.notes, e.createdAt))
                added.events++
            }
        }

        for (c in d.contacts) {
            val entity = ContactEntity(
                c.id, c.firstName, c.lastName, c.jobTitle, c.companyId?.let { companyIds[it] }, c.country, c.phone, c.mobile, c.email,
                c.website, c.address, c.notes, c.exportedToPhoneAt, c.createdAt, c.updatedAt,
            )
            val existing = db.contactDao().get(c.id)
            when {
                existing == null -> {
                    db.contactDao().insert(entity)
                    added.contacts++
                    touchedContacts += c.id
                }
                c.updatedAt > existing.updatedAt -> {
                    db.contactDao().update(entity)
                    updated.contacts++
                    touchedContacts += c.id
                }
                else -> skipped.contacts++
            }
        }

        for (link in d.contactEvents) {
            if (link.eventId in db.contactDao().eventIds(link.contactId)) {
                skipped.contactEvents++
            } else {
                db.contactDao().addToEvent(ContactEventEntity(link.contactId, link.eventId))
                added.contactEvents++
                touchedContacts += link.contactId
            }
        }

        // Cards have no update date: an existing one is kept with its images.
        for (k in d.businessCards) {
            if (db.businessCardDao().get(k.id) != null) {
                skipped.cards++
                continue
            }
            db.businessCardDao().insert(
                BusinessCardEntity(
                    k.id, k.contactId, k.eventId, k.frontOriginal, k.frontProcessed, k.backOriginal, k.backProcessed, k.frontQuad, k.backQuad,
                    k.rawOcrText, k.extractedJson, k.ocrEngine, k.ocrStatus, k.isPrimary, k.capturedAt, k.createdAt,
                ),
            )
            added.cards++
            images += listOfNotNull(k.frontOriginal, k.frontProcessed, k.backOriginal, k.backProcessed)
            k.contactId?.let { touchedContacts += it }
        }
        return Imported(added, updated, skipped, images, touchedContacts)
    }

    /** One primary card per contact (the newest primary, or the newest card), after cards were merged in. */
    private suspend fun fixPrimaryCards(contactIds: Set<String>) {
        for (id in contactIds) {
            val cards = db.businessCardDao().byContact(id)
            if (cards.isEmpty()) continue
            val primaries = cards.filter { it.isPrimary }
            if (primaries.size != 1) db.businessCardDao().setPrimary(id, primaries.firstOrNull()?.id ?: cards.first().id)
        }
    }

    /** Second pass: copies the [wanted] images (store paths) into [staging]. Fails if one of them is missing. */
    private suspend fun stage(open: () -> InputStream, password: CharArray?, wanted: Set<String>, staging: File, onProgress: (Float) -> Unit) {
        if (wanted.isEmpty()) return
        val pending = wanted.toMutableSet()
        open().use { raw ->
            zip(raw, password).use { zip ->
                while (pending.isNotEmpty()) {
                    val header = zip.nextEntry ?: break
                    if (header.isDirectory || !header.fileName.startsWith(BackupFormat.IMAGES)) continue
                    val path = BackupFormat.storagePathFor(header.fileName)
                    if (path !in pending) continue
                    store.stageImage(staging, path, zip)
                    pending -= path
                    onProgress(1f - pending.size.toFloat() / wanted.size)
                }
            }
        }
        if (pending.isNotEmpty()) throw IOException("Missing images: ${pending.take(MAX_LISTED)}")
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_LISTED = 3
        const val PROGRESS_READ = 0.2f
        const val PROGRESS_DATABASE = 0.4f
        const val PROGRESS_IMAGES = 0.95f

        /** The files a backup may contain (docs/05 §2). */
        val ALLOWED_ENTRY = Regex(
            "manifest\\.json|database\\.json|settings\\.json|images/([A-Za-z0-9-]{1,64}/((front|back)(_original)?\\.jpg)?)?",
        )
    }
}

package com.qtekfun.ultimatevisitcards.data.backup

import androidx.room3.withReadTransaction
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IoDispatcher
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

/** What to put in the backup. A null or empty [password] means no encryption. */
class BackupOptions(
    val appVersion: String,
    val includeOriginals: Boolean = true,
    val password: CharArray? = null,
    val settings: BackupSettings = BackupSettings()
)

sealed interface BackupResult {
    /** Written and verified (reopened: every entry readable, sizes as expected). */
    data class Done(val manifest: BackupManifest) : BackupResult

    /** Written, but reading it back did not match: the file must not be trusted. */
    data object VerificationFailed : BackupResult

    data class Failed(val cause: Throwable) : BackupResult
}

/**
 * Full backup export (docs/05 §2): a consistent snapshot of the database, the card images and the
 * non-sensitive settings, streamed into a deflated ZIP, every entry AES-256 encrypted
 * when a password is given (openable with 7-Zip). Ids are kept as they are. Afterwards the file is read
 * back and checked. Cancelling the coroutine stops between entries; the caller deletes the partial file.
 */
class BackupExporter @Inject constructor(
    private val db: AppDatabase,
    private val store: ImageStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    /** Writes the backup to [out] (closed afterwards) and verifies it by reading [reopen]. */
    suspend fun export(out: OutputStream, reopen: () -> InputStream, options: BackupOptions, onProgress: (Float) -> Unit = {}): BackupResult = withContext(io) {
        try {
            val database = snapshot()
            val images = imagesOf(database, options.includeOriginals)
            val manifest = BackupManifest(
                backupVersion = BackupFormat.VERSION,
                appVersion = options.appVersion,
                createdAt = clock.nowMillis(),
                counts = BackupCounts(
                    contacts = database.contacts.size,
                    companies = database.companies.size,
                    sectors = database.sectors.size,
                    events = database.events.size,
                    contactEvents = database.contactEvents.size,
                    businessCards = database.businessCards.size,
                    images = images.size
                ),
                includesOriginals = options.includeOriginals,
                encrypted = options.password?.isNotEmpty() == true
            )
            val expected = write(out, manifest, database, options, images, onProgress)
            if (verify(reopen, options.password, expected)) BackupResult.Done(manifest) else BackupResult.VerificationFailed
        } catch (e: IOException) {
            BackupResult.Failed(e)
        }
    }

    private suspend fun snapshot(): BackupDatabase = db.withReadTransaction {
        val dao = db.exportDao()
        BackupDatabase(
            sectors = dao.sectors().map { BackupSector(it.id, it.name, it.sortOrder, it.createdAt) },
            companies = dao.companies().map { BackupCompany(it.id, it.name, it.sectorId, it.country, it.website, it.notes, it.createdAt, it.updatedAt) },
            events = dao.events().map { BackupEvent(it.id, it.name, it.location, it.startDate, it.endDate, it.notes, it.createdAt) },
            contacts = dao.contacts().map {
                BackupContact(
                    it.id, it.firstName, it.lastName, it.jobTitle, it.companyId, it.country, it.phone, it.mobile, it.email, it.website, it.address,
                    it.notes, it.exportedToPhoneAt, it.createdAt, it.updatedAt
                )
            },
            contactEvents = dao.contactEvents().map { BackupContactEvent(it.contactId, it.eventId) },
            businessCards = dao.cards().map {
                BackupCard(
                    it.id, it.contactId, it.eventId, it.frontOriginal, it.frontProcessed, it.backOriginal, it.backProcessed, it.frontQuad, it.backQuad,
                    it.rawOcrText, it.extractedJson, it.ocrEngine, it.ocrStatus, it.isPrimary, it.capturedAt, it.createdAt
                )
            }
        )
    }

    /** Existing image files to include, by storage path (a missing file is left out, never invented). */
    private fun imagesOf(database: BackupDatabase, includeOriginals: Boolean): List<Pair<String, File>> = database.businessCards.flatMap { card ->
        listOfNotNull(card.frontProcessed, card.backProcessed) +
            if (includeOriginals) listOfNotNull(card.frontOriginal, card.backOriginal) else emptyList()
    }.distinct().mapNotNull { path ->
        try {
            store.file(path).takeIf { it.isFile }?.let { path to it }
        } catch (e: IOException) {
            null
        }
    }

    /** Streams every entry; returns the expected uncompressed size of each one, for the verification. */
    private suspend fun write(
        out: OutputStream,
        manifest: BackupManifest,
        database: BackupDatabase,
        options: BackupOptions,
        images: List<Pair<String, File>>,
        onProgress: (Float) -> Unit
    ): Map<String, Long> {
        val expected = linkedMapOf<String, Long>()
        val password = options.password?.takeIf { it.isNotEmpty() }
        val zip = if (password != null) ZipOutputStream(out, password) else ZipOutputStream(out)
        zip.use {
            fun json(name: String, content: String) {
                val bytes = content.toByteArray(Charsets.UTF_8)
                zip.putNextEntry(parameters(name, CompressionMethod.DEFLATE, password != null, CompressionLevel.NORMAL))
                zip.write(bytes)
                zip.closeEntry()
                expected[name] = bytes.size.toLong()
            }
            json(BackupFormat.MANIFEST, BackupFormat.json.encodeToString(BackupManifest.serializer(), manifest))
            json(BackupFormat.DATABASE, BackupFormat.json.encodeToString(BackupDatabase.serializer(), database))
            json(BackupFormat.SETTINGS, BackupFormat.json.encodeToString(BackupSettings.serializer(), options.settings))
            images.forEachIndexed { i, (path, file) ->
                kotlin.coroutines.coroutineContext.ensureActive()
                val name = BackupFormat.entryFor(path)
                // JPEGs barely compress: fastest deflate. Not STORE, whose data descriptors streaming readers reject.
                zip.putNextEntry(parameters(name, CompressionMethod.DEFLATE, password != null, CompressionLevel.FASTEST))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
                expected[name] = file.length()
                onProgress((i + 1).toFloat() / images.size)
            }
        }
        return expected
    }

    private fun parameters(name: String, method: CompressionMethod, encrypt: Boolean, level: CompressionLevel) = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = method
        compressionLevel = level
        if (encrypt) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }

    /** Reads the whole file back (decrypting, checking CRCs) and compares entries and sizes. */
    private fun verify(reopen: () -> InputStream, password: CharArray?, expected: Map<String, Long>): Boolean = try {
        val found = linkedMapOf<String, Long>()
        val buffer = ByteArray(BUFFER)
        ZipInputStream(reopen(), password?.takeIf { it.isNotEmpty() }).use { zip ->
            while (true) {
                val header = zip.nextEntry ?: break
                var size = 0L
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    size += n
                }
                found[header.fileName] = size
            }
        }
        found == expected
    } catch (e: ZipException) {
        false
    } catch (e: IOException) {
        false
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

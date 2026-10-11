package com.qtekfun.ultimatevisitcards.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Restore (docs/05 §3 and §6 bis) from archives built here with the JDK's ZIP writer (or zip4j for AES),
 * independent of the exporter. Synthetic data; the «images» are small byte blobs (restore copies bytes).
 */
@RunWith(AndroidJUnit4::class)
class BackupRestorerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val files by lazy { folder.newFolder("files") }
    private val cache by lazy { folder.newFolder("cache") }
    private val store by lazy { ImageStore(files, cache, dispatcher) }
    private val restorer by lazy { BackupRestorer(data.db, store, data.indexer, dispatcher) }
    private val json = BackupFormat.json

    @After
    fun close() = data.close()

    // --- Synthetic backups -----------------------------------------------------------------------------

    private fun card(id: String, contactId: String?, back: Boolean = false, capturedAt: Long = 5, primary: Boolean = contactId != null) = BackupCard(
        id = id,
        contactId = contactId,
        eventId = if (contactId != null) "e1" else null,
        frontOriginal = "cards/$id/front_original.jpg",
        frontProcessed = "cards/$id/front.jpg",
        backOriginal = if (back) "cards/$id/back_original.jpg" else null,
        backProcessed = if (back) "cards/$id/back.jpg" else null,
        frontQuad = """{"q":1}""",
        rawOcrText = "JOSE MUNOZ ABC MARINE",
        extractedJson = "{}",
        ocrEngine = "mlkit",
        ocrStatus = if (contactId != null) "REVIEWED" else "PENDING_REVIEW",
        isPrimary = primary,
        capturedAt = capturedAt,
        createdAt = capturedAt,
    )

    private fun world() = BackupDatabase(
        sectors = listOf(BackupSector("s1", "Astillero", 0, 1)),
        companies = listOf(BackupCompany("co1", "ABC Marine GmbH", sectorId = "s1", country = "Germany", website = "abc-demo.de", createdAt = 1, updatedAt = 10)),
        events = listOf(BackupEvent("e1", "SMM 2026", location = "Hamburg", startDate = 100, endDate = 200, createdAt = 1)),
        contacts = listOf(
            BackupContact("p1", "José", "Muñoz", jobTitle = "Director", companyId = "co1", email = "jose@abc-demo.de", notes = "Met at SMM", createdAt = 1, updatedAt = 10),
            BackupContact("p2", "Ana", "Ruiz", country = "Spain", createdAt = 2, updatedAt = 20),
        ),
        contactEvents = listOf(BackupContactEvent("p1", "e1")),
        businessCards = listOf(card("k1", "p1", back = true), card("k2", null)),
    )

    private fun images(d: BackupDatabase): Map<String, ByteArray> = d.businessCards
        .flatMap { listOfNotNull(it.frontOriginal, it.frontProcessed, it.backOriginal, it.backProcessed) }
        .associateWith { "jpeg bytes of $it".toByteArray() }

    private fun manifest(d: BackupDatabase, version: Int = BackupFormat.VERSION, encrypted: Boolean = false, originals: Boolean = true) = BackupManifest(
        backupVersion = version,
        appVersion = "test",
        createdAt = 1,
        counts = BackupCounts(d.contacts.size, d.companies.size, d.sectors.size, d.events.size, d.contactEvents.size, d.businessCards.size, images(d).size),
        includesOriginals = originals,
        encrypted = encrypted,
    )

    private fun entries(
        d: BackupDatabase = world(),
        images: Map<String, ByteArray> = images(d),
        version: Int = BackupFormat.VERSION,
        databaseJson: String = json.encodeToString(BackupDatabase.serializer(), d),
    ): List<Pair<String, ByteArray>> = listOf(
        BackupFormat.MANIFEST to json.encodeToString(BackupManifest.serializer(), manifest(d, version)).toByteArray(),
        BackupFormat.DATABASE to databaseJson.toByteArray(),
        BackupFormat.SETTINGS to json.encodeToString(BackupSettings.serializer(), BackupSettings(cardsView = "LIST")).toByteArray(),
    ) + images.map { (path, bytes) -> BackupFormat.entryFor(path) to bytes }

    /** A plain ZIP written with java.util.zip (keeps entry names exactly as given). */
    private fun plainZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** An AES-256 ZIP written with zip4j, as the exporter does. */
    private fun aesZip(entries: List<Pair<String, ByteArray>>, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        net.lingala.zip4j.io.outputstream.ZipOutputStream(out, password.toCharArray()).use { zip ->
            entries.forEach { (name, bytes) ->
                val params = ZipParameters()
                params.setFileNameInZip(name)
                params.setCompressionMethod(CompressionMethod.DEFLATE)
                params.setEncryptFiles(true)
                params.setEncryptionMethod(EncryptionMethod.AES)
                params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256)
                zip.putNextEntry(params)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    private suspend fun restore(bytes: ByteArray, mode: RestoreMode = RestoreMode.MERGE, password: String? = null): RestoreReport =
        assertIs<RestoreOutcome.Done>(restorer.restore(open(bytes), password?.toCharArray(), mode)).report

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun storedFiles(): Set<String> = File(files, "cards").walkTopDown().filter { it.isFile }.map { it.relativeTo(files).path }.toSet()

    // --- Round trip and idempotency --------------------------------------------------------------------

    @Test
    fun `round trip into an empty database keeps every row, id and image`() = runTest(dispatcher) {
        val d = world()
        val report = restore(plainZip(entries(d)))

        assertEquals(RestoreCounts(sectors = 1, companies = 1, events = 1, contacts = 2, contactEvents = 1, cards = 2), report.added)
        assertEquals(RestoreCounts(), report.updated)
        assertEquals(6, report.images)
        assertEquals("LIST", report.settings?.cardsView)

        assertEquals(listOf(SectorEntity("s1", "Astillero", "astillero", 0, 1)), data.db.sectorDao().all())
        assertEquals(listOf(CompanyEntity("co1", "ABC Marine GmbH", "abc marine", "s1", "Germany", "abc-demo.de", null, 1, 10)), data.db.companyDao().all())
        assertEquals(d.contacts.map { it.id }.toSet(), data.db.contactDao().all().map { it.id }.toSet())
        assertEquals("Met at SMM", data.db.contactDao().get("p1")!!.notes)
        assertEquals(listOf("e1"), data.db.contactDao().eventIds("p1"))
        val k1 = data.db.businessCardDao().get("k1")!!
        assertEquals(BusinessCardEntity("k1", "p1", "e1", "cards/k1/front_original.jpg", "cards/k1/front.jpg", "cards/k1/back_original.jpg", "cards/k1/back.jpg", """{"q":1}""", null, "JOSE MUNOZ ABC MARINE", "{}", "mlkit", "REVIEWED", true, 5, 5), k1)
        // Images byte-identical.
        images(d).forEach { (path, bytes) -> assertEquals(sha(bytes), sha(store.file(path).readBytes()), path) }
        // The search index was rebuilt.
        assertEquals(listOf("p1"), data.contacts.search("munoz abc").first().map { it.id })
        assertFalse(files.listFiles()!!.any { it.name.startsWith(".restore-") })
    }

    @Test
    fun `restoring the same backup twice changes nothing the second time`() = runTest(dispatcher) {
        val bytes = plainZip(entries())
        restore(bytes)
        val before = data.db.contactDao().all()
        val second = restore(bytes)
        assertEquals(RestoreCounts(), second.added)
        assertEquals(RestoreCounts(), second.updated)
        assertEquals(RestoreCounts(sectors = 1, companies = 1, events = 1, contacts = 2, contactEvents = 1, cards = 2), second.skipped)
        assertEquals(0, second.images)
        assertEquals(before, data.db.contactDao().all())
    }

    // --- Merge rules -----------------------------------------------------------------------------------

    @Test
    fun `merge updates only newer rows and links companies and sectors by name`() = runTest(dispatcher) {
        // Already on the phone: the same sector and company under other ids, p1 older, p2 newer.
        data.db.sectorDao().insert(SectorEntity("sx", "ASTILLERO", "astillero", 0, 1))
        data.db.companyDao().insert(CompanyEntity("cx", "ABC MARINE", "abc marine", null, null, null, null, 1, 1))
        data.db.contactDao().insert(ContactEntity("p1", "José", "Muñoz", "Old title", null, null, null, null, null, null, null, null, null, 1, 5))
        data.db.contactDao().insert(ContactEntity("p2", "Ana", "Ruiz", "Newer here", null, null, null, null, null, null, null, null, null, 2, 30))

        val report = restore(plainZip(entries()))

        assertEquals(1, report.updated.contacts)
        assertEquals(1, report.skipped.contacts)
        assertEquals(1, report.skipped.companies)
        assertEquals(1, report.skipped.sectors)
        // No duplicates: the backup's ids were remapped to the existing ones.
        assertEquals(listOf("sx"), data.db.sectorDao().all().map { it.id })
        assertEquals(listOf("cx"), data.db.companyDao().all().map { it.id })
        val p1 = data.db.contactDao().get("p1")!!
        assertEquals("Director", p1.jobTitle)
        assertEquals("cx", p1.companyId)
        assertEquals("Newer here", data.db.contactDao().get("p2")!!.jobTitle)
    }

    @Test
    fun `merging cards leaves exactly one primary card per contact`() = runTest(dispatcher) {
        restore(plainZip(entries()))
        // A later backup brings a newer card, also marked primary, for the same contact.
        val d = world().let { it.copy(businessCards = it.businessCards + card("k3", "p1", capturedAt = 9)) }
        restore(plainZip(entries(d)))
        val cards = data.db.businessCardDao().byContact("p1")
        assertEquals(listOf("k3", "k1"), cards.map { it.id })
        assertEquals(1, cards.count { it.isPrimary })
    }

    @Test
    fun `replace deletes what was there, rows and images`() = runTest(dispatcher) {
        data.db.contactDao().insert(ContactEntity("old", "Old", "Contact", null, null, null, null, null, null, null, null, null, null, 1, 1))
        data.db.businessCardDao().insert(BusinessCardEntity("oldcard", "old", null, "cards/oldcard/front_original.jpg", "cards/oldcard/front.jpg", null, null, null, null, "", null, null, "REVIEWED", true, 1, 1))
        File(files, "cards/oldcard").mkdirs()
        File(files, "cards/oldcard/front.jpg").writeText("old")

        val report = restore(plainZip(entries()), RestoreMode.REPLACE)

        assertEquals(2, report.added.contacts)
        assertEquals(setOf("p1", "p2"), data.db.contactDao().all().map { it.id }.toSet())
        assertEquals(null, data.db.businessCardDao().get("oldcard"))
        assertFalse(File(files, "cards/oldcard").exists())
        assertEquals(images(world()).keys, storedFiles())
    }

    // --- Validation ------------------------------------------------------------------------------------

    @Test
    fun `a valid backup is described before restoring`() = runTest(dispatcher) {
        val ready = assertIs<InspectResult.Ready>(restorer.inspect(open(plainZip(entries())), null))
        assertEquals(BackupCounts(contacts = 2, companies = 1, sectors = 1, events = 1, contactEvents = 1, businessCards = 2, images = 6), ready.counts)
        assertEquals(BackupFormat.VERSION, ready.manifest.backupVersion)
        // Inspecting writes nothing.
        assertTrue(data.db.contactDao().all().isEmpty())
    }

    @Test
    fun `damaged files are told apart from other files`() = runTest(dispatcher) {
        assertEquals(InspectResult.NotABackup, restorer.inspect(open("just some text, not a zip".toByteArray()), null))
        // Any other ZIP.
        assertEquals(InspectResult.NotABackup, restorer.inspect(open(plainZip(listOf("notes/hello.txt" to "hi".toByteArray()))), null))

        val whole = plainZip(entries())
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(whole.copyOf(whole.size * 2 / 3)), null))
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(plainZip(entries(databaseJson = "{broken"))), null))
        val missing = images(world()) - "cards/k1/back.jpg"
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(plainZip(entries(images = missing))), null))
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(plainZip(entries().filter { it.first != BackupFormat.DATABASE })), null))
        // Inconsistent data: a contact of a company that is not in the backup.
        val orphan = world().let { it.copy(companies = emptyList()) }
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(plainZip(entries(orphan))), null))
        // Nothing is restored from a damaged file.
        assertIs<RestoreOutcome.Rejected>(restorer.restore(open(whole.copyOf(whole.size / 2)), null, RestoreMode.MERGE))
        assertTrue(data.db.contactDao().all().isEmpty())
    }

    @Test
    fun `a backup from a newer version is refused`() = runTest(dispatcher) {
        assertEquals(InspectResult.TooNew(BackupFormat.VERSION + 1), restorer.inspect(open(plainZip(entries(version = BackupFormat.VERSION + 1))), null))
    }

    @Test
    fun `an encrypted backup needs its password, and a wrong one is not a damaged file`() = runTest(dispatcher) {
        val bytes = aesZip(entries(), "s3cret pass")
        assertEquals(InspectResult.NeedsPassword, restorer.inspect(open(bytes), null))
        assertEquals(InspectResult.WrongPassword, restorer.inspect(open(bytes), "nope".toCharArray()))
        assertIs<InspectResult.Ready>(restorer.inspect(open(bytes), "s3cret pass".toCharArray()))

        val report = restore(bytes, password = "s3cret pass")
        assertEquals(2, report.added.contacts)
        assertEquals(sha("jpeg bytes of cards/k1/front.jpg".toByteArray()), sha(store.file("cards/k1/front.jpg").readBytes()))
    }

    @Test
    fun `entries trying to escape the archive are refused (zip slip)`() = runTest(dispatcher) {
        for (evil in listOf("../evil.jpg", "/abs/evil.jpg", "images/../../evil.jpg", "images\\k1\\front.jpg", "images/k1/../../../evil.jpg", "other/file.txt")) {
            val result = restorer.restore(open(plainZip(entries() + (evil to "x".toByteArray()))), null, RestoreMode.MERGE)
            assertIs<InspectResult.Corrupt>(assertIs<RestoreOutcome.Rejected>(result).reason, evil)
        }
        // A card pointing at another card's folder is refused too.
        val crossed = world().let { d -> d.copy(businessCards = d.businessCards.map { if (it.id == "k2") it.copy(frontProcessed = "cards/k1/front.jpg") else it }) }
        assertIs<InspectResult.Corrupt>(restorer.inspect(open(plainZip(entries(crossed))), null))
        assertTrue(data.db.contactDao().all().isEmpty())
        assertFalse(folder.root.walkTopDown().any { it.name == "evil.jpg" })
    }

    // --- Rollback --------------------------------------------------------------------------------------

    /** A stream that breaks after [limit] bytes. */
    private class Breaking(private val delegate: InputStream, private val limit: Int) : InputStream() {
        private var read = 0

        override fun read(): Int {
            if (read >= limit) throw IOException("Disk error")
            read++
            return delegate.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (read >= limit) throw IOException("Disk error")
            val n = delegate.read(b, off, minOf(len, limit - read))
            if (n > 0) read += n
            return n
        }
    }

    @Test
    fun `a failure while copying images rolls everything back`() = runTest(dispatcher) {
        data.db.contactDao().insert(ContactEntity("keep", "Kept", "Contact", null, null, null, null, null, null, null, null, null, null, 1, 1))
        val bytes = plainZip(entries())
        var opens = 0
        // First pass (validation) reads fine; the second one (images) breaks halfway.
        val open = { opens++.let { if (it == 0) ByteArrayInputStream(bytes) else Breaking(ByteArrayInputStream(bytes), bytes.size / 2) } }

        val outcome = restorer.restore(open, null, RestoreMode.REPLACE)

        assertIs<RestoreOutcome.Failed>(outcome)
        assertEquals(listOf("keep"), data.db.contactDao().all().map { it.id })
        assertTrue(data.db.businessCardDao().all().isEmpty())
        assertTrue(storedFiles().isEmpty())
        assertFalse(files.listFiles()!!.any { it.name.startsWith(".restore-") })
    }

    // --- Volume ----------------------------------------------------------------------------------------

    @Test
    fun `two thousand contacts`() = runTest(dispatcher) {
        val contacts = (0 until 2_000).map { BackupContact("c$it", "Name$it", "Surname$it", companyId = "co1", createdAt = it.toLong(), updatedAt = it.toLong()) }
        val d = world().copy(contacts = contacts, contactEvents = contacts.map { BackupContactEvent(it.id, "e1") }, businessCards = emptyList())
        val report = restore(plainZip(entries(d)))
        assertEquals(2_000, report.added.contacts)
        assertEquals(2_000, report.added.contactEvents)
        assertEquals(2_000, data.db.searchDao().count())
        assertEquals(listOf("c1999"), data.contacts.search("surname1999").first().map { it.id })
    }
}

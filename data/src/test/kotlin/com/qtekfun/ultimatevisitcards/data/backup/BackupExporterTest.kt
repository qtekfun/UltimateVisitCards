package com.qtekfun.ultimatevisitcards.data.backup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream as JdkZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupExporterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)
    private val store by lazy { ImageStore(folder.newFolder("files"), folder.newFolder("cache"), dispatcher) }
    private val exporter by lazy { BackupExporter(data.db, store, data.clock, dispatcher) }

    @After
    fun close() = data.close()

    private fun photo() = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 100, 50)) }

    /** 2 contacts, a company, a sector, an event, 2 cards (one with back), 6 image files. */
    private suspend fun world(): List<String> {
        val sector = data.sectors.create("Astillero").value()
        val company = data.companies.create(CompanyDraft("Ejemplo Naval S.L.", sector.id, "Spain")).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val ana = data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = company.id, notes = "Línea 1\n«comillas»")).value()
        data.contacts.addToEvent(ana.id, smm.id).value()
        data.contacts.create(ContactDraft("Luis", "Gil")).value()
        val ids = listOf("aaaaaaaa-0000-4000-8000-000000000001", "aaaaaaaa-0000-4000-8000-000000000002")
        ids.forEachIndexed { i, id ->
            store.saveOriginal(id, CardSide.FRONT, photo())
            store.saveProcessed(id, CardSide.FRONT, photo())
            if (i == 0) {
                store.saveOriginal(id, CardSide.BACK, photo())
                store.saveProcessed(id, CardSide.BACK, photo())
            }
            data.cards.create(
                BusinessCardDraft(
                    store.originalPath(id, CardSide.FRONT),
                    store.processedPath(id, CardSide.FRONT),
                    if (i == 0) store.originalPath(id, CardSide.BACK) else null,
                    if (i == 0) store.processedPath(id, CardSide.BACK) else null,
                    capturedAt = i.toLong()
                ),
                id = id
            ).value()
        }
        data.cards.attachToContact(ids[0], ana.id).value()
        return ids
    }

    private fun entries(bytes: ByteArray, password: CharArray? = null): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes), password).use { zip ->
            while (true) {
                val header = zip.nextEntry ?: break
                out[header.fileName] = zip.readBytes()
            }
        }
        return out
    }

    private suspend fun export(options: BackupOptions, tamper: (ByteArray) -> ByteArray = { it }): Pair<BackupResult, ByteArray> {
        val out = ByteArrayOutputStream()
        val result = exporter.export(out, { ByteArrayInputStream(tamper(out.toByteArray())) }, options)
        return result to out.toByteArray()
    }

    @Test
    fun `a full backup has the manifest, the database with the same ids and every image byte for byte`() = runTest(dispatcher) {
        val ids = world()
        val (result, bytes) = export(BackupOptions(appVersion = "0.9.0"))
        val manifest = assertIs<BackupResult.Done>(result).manifest
        assertEquals(BackupCounts(contacts = 2, companies = 1, sectors = 1, events = 1, contactEvents = 1, businessCards = 2, images = 6), manifest.counts)
        assertEquals(false, manifest.encrypted)

        val files = entries(bytes)
        assertEquals(listOf("manifest.json", "database.json", "settings.json"), files.keys.take(3))
        val database = BackupFormat.json.decodeFromString(BackupDatabase.serializer(), files.getValue("database.json").toString(Charsets.UTF_8))
        assertEquals(ids.toSet(), database.businessCards.map { it.id }.toSet())
        assertEquals("Línea 1\n«comillas»", database.contacts.single { it.lastName == "Ruiz" }.notes)
        val front = store.file(store.processedPath(ids[0], CardSide.FRONT)).readBytes()
        assertTrue(front.contentEquals(files.getValue("images/${ids[0]}/front.jpg")))
        // Plain backups open with any ZIP tool.
        val jdk = mutableListOf<String>()
        JdkZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry }.forEach { jdk += it.name } }
        assertEquals(files.keys.toList(), jdk)
    }

    @Test
    fun `without originals only the straightened images go in`() = runTest(dispatcher) {
        world()
        val (result, bytes) = export(BackupOptions(appVersion = "x", includeOriginals = false))
        assertEquals(3, assertIs<BackupResult.Done>(result).manifest.counts.images)
        assertTrue(entries(bytes).keys.none { it.contains("_original") })
    }

    @Test
    fun `an encrypted backup needs its password for every entry`() = runTest(dispatcher) {
        world()
        val password = "correct horse".toCharArray()
        val (result, bytes) = export(BackupOptions(appVersion = "x", password = password.copyOf()))
        assertTrue(assertIs<BackupResult.Done>(result).manifest.encrypted)
        ZipInputStream(ByteArrayInputStream(bytes), password).use { zip ->
            generateSequence { zip.nextEntry }.forEach { assertTrue(it.isEncrypted, it.fileName) }
        }
        assertEquals(9, entries(bytes, password).size)
        assertFailsWith<ZipException> { entries(bytes, "wrong".toCharArray()) }
    }

    @Test
    fun `a file that does not read back the same is reported`() = runTest(dispatcher) {
        world()
        val (result, _) = export(BackupOptions(appVersion = "x")) { it.copyOf(it.size / 2) }
        assertEquals(BackupResult.VerificationFailed, result)
    }

    @Test
    fun `cancelling stops the export`() = runTest(dispatcher) {
        world()
        val out = ByteArrayOutputStream()
        assertFailsWith<CancellationException> {
            exporter.export(out, { ByteArrayInputStream(out.toByteArray()) }, BackupOptions(appVersion = "x")) {
                throw CancellationException("user")
            }
        }
    }

    @Test
    fun `an empty database still makes a valid backup`() = runTest(dispatcher) {
        val (result, bytes) = export(BackupOptions(appVersion = "x"))
        assertEquals(0, assertIs<BackupResult.Done>(result).manifest.counts.contacts)
        assertEquals(setOf("manifest.json", "database.json", "settings.json"), entries(bytes).keys)
        assertTrue(File(folder.root, "files").exists())
    }
}

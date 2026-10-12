package com.qtekfun.ultimatevisitcards.data.backup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * docs/05 §6 bis, «Round-trip»: what one phone exports, another (empty) one restores identically — every
 * entity equal, every image identical by SHA-256 — with and without encryption, and twice changes nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupRoundTripTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val source = TestData(dispatcher)
    private val target = TestData(dispatcher)
    private val sourceStore by lazy { ImageStore(folder.newFolder("a-files"), folder.newFolder("a-cache"), dispatcher) }
    private val targetStore by lazy { ImageStore(folder.newFolder("b-files"), folder.newFolder("b-cache"), dispatcher) }

    @After
    fun close() {
        source.close()
        target.close()
    }

    private fun photo(color: Int) = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private suspend fun fill() {
        val sector = source.sectors.create("Astillero").value()
        val company = source.companies.create(CompanyDraft("Example Heavy Industries Co., Ltd.", sector.id, "South Korea")).value()
        val smm = source.events.create(EventDraft("SMM 2026", location = "Hamburg", startDate = 10)).value()
        repeat(3) { i ->
            val contact = source.contacts.create(
                ContactDraft("Ji-ho $i", "Park", jobTitle = "Engineer", companyId = company.id, notes = "nota $i\n«ñ»")
            ).value()
            source.contacts.addToEvent(contact.id, smm.id).value()
            val cardId = "bbbbbbbb-0000-4000-8000-00000000000$i"
            sourceStore.saveOriginal(cardId, CardSide.FRONT, photo(Color.rgb(10 * i, 100, 200)))
            sourceStore.saveProcessed(cardId, CardSide.FRONT, photo(Color.rgb(200, 10 * i, 100)))
            source.cards.create(
                BusinessCardDraft(
                    sourceStore.originalPath(cardId, CardSide.FRONT),
                    sourceStore.processedPath(cardId, CardSide.FRONT),
                    eventId = smm.id,
                    capturedAt = i.toLong()
                ),
                id = cardId
            ).value()
            source.cards.attachToContact(cardId, contact.id).value()
        }
    }

    private fun hashes(root: File): Map<String, String> = root.walkTopDown().filter { it.isFile && it.extension == "jpg" }.associate { file ->
        file.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun snapshot(data: TestData) = with(data.db.exportDao()) { listOf(sectors(), companies(), events(), contacts(), contactEvents(), cards()) }

    private suspend fun roundTrip(password: String?) {
        fill()
        val bytes = ByteArrayOutputStream().also { out ->
            val result = BackupExporter(source.db, sourceStore, source.clock, dispatcher)
                .export(out, { ByteArrayInputStream(out.toByteArray()) }, BackupOptions(appVersion = "test", password = password?.toCharArray()))
            assertIs<BackupResult.Done>(result)
        }.toByteArray()

        val restorer = BackupRestorer(target.db, targetStore, SearchIndexer(target.db), dispatcher)
        val pass = password?.toCharArray()
        assertIs<InspectResult.Ready>(restorer.inspect({ ByteArrayInputStream(bytes) }, pass))
        val report = assertIs<RestoreOutcome.Done>(restorer.restore({ ByteArrayInputStream(bytes) }, pass, RestoreMode.MERGE)).report
        assertEquals(3, report.added.contacts)
        assertEquals(6, report.images)

        assertEquals(snapshot(source), snapshot(target))
        assertEquals(hashes(File(folder.root, "a-files")), hashes(File(folder.root, "b-files")))
        // The restored phone finds contacts as the original one did.
        assertEquals(3, target.contacts.observeList(ContactFilter(text = "smm astillero korea")).first().size)

        // Restoring the same backup again changes nothing.
        val again = assertIs<RestoreOutcome.Done>(restorer.restore({ ByteArrayInputStream(bytes) }, pass, RestoreMode.MERGE)).report
        assertEquals(0, again.added.contacts + again.updated.contacts + again.added.cards + again.updated.cards)
        assertEquals(snapshot(source), snapshot(target))
    }

    @Test
    fun `plain backup round trip`() = runTest(dispatcher) { roundTrip(password = null) }

    @Test
    fun `encrypted backup round trip`() = runTest(dispatcher) { roundTrip(password = "una contraseña larga") }

    @Test
    fun `with the originals discarded in Settings, the backup does not claim them and restores fine`() = runTest(dispatcher) {
        fill()
        assertEquals(3, sourceStore.deleteAllOriginals())
        val bytes = ByteArrayOutputStream().also { out ->
            val result = BackupExporter(source.db, sourceStore, source.clock, dispatcher)
                .export(out, { ByteArrayInputStream(out.toByteArray()) }, BackupOptions(appVersion = "test"))
            assertEquals(false, assertIs<BackupResult.Done>(result).manifest.includesOriginals)
        }.toByteArray()

        val restorer = BackupRestorer(target.db, targetStore, SearchIndexer(target.db), dispatcher)
        assertIs<InspectResult.Ready>(restorer.inspect({ ByteArrayInputStream(bytes) }, null))
        val report = assertIs<RestoreOutcome.Done>(restorer.restore({ ByteArrayInputStream(bytes) }, null, RestoreMode.MERGE)).report
        assertEquals(3, report.added.contacts)
        assertEquals(3, report.images)
        assertEquals(hashes(File(folder.root, "a-files")), hashes(File(folder.root, "b-files")))
    }
}

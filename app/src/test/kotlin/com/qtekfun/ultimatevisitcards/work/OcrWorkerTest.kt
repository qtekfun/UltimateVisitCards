package com.qtekfun.ultimatevisitcards.work

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.extract.CardFieldExtractor
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.ocr.NormRect
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrBlock
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrException
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrLine
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The OCR state machine of one card, with a scripted engine and real database and files. */
@RunWith(AndroidJUnit4::class)
class OcrWorkerTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Answers with [results] in order, or fails when the next answer is null. */
    private class ScriptedOcr(vararg results: OcrResult?) : OcrService {
        val answers = ArrayDeque(results.toList())
        val seen = mutableListOf<File>()
        var released = 0

        override val engineId = "scripted"

        override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult {
            seen += image.file
            return answers.removeFirst() ?: throw OcrException("unreadable")
        }

        override suspend fun release() {
            released++
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var store: ImageStore
    private lateinit var cards: RoomBusinessCardRepository
    private val cardId = "33333333-3333-4333-8333-333333333333"

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor { }.build())
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        store = ImageStore(folder.newFolder("files"), folder.newFolder("cache"), kotlinx.coroutines.Dispatchers.Unconfined)
        cards = RoomBusinessCardRepository(db, SearchIndexer(db), Clock { 1L }, IdGenerator.Uuid)
    }

    @After
    fun tearDown() = db.close()

    private fun line(text: String, top: Float) = OcrLine(text, NormRect(0f, top, 1f, top + 0.05f), 0.95f, 12f)

    private fun result(vararg lines: String) = OcrResult(
        lines.joinToString("\n"),
        lines.mapIndexed { i, t -> line(t, i * 0.1f).let { OcrBlock(t, listOf(it), it.box) } }
    )

    private fun card(back: Boolean = false) = runBlocking {
        listOf("front", "back").forEach { File(folder.root, "files/cards/$cardId").mkdirs() }
        File(folder.root, "files/cards/$cardId/front.jpg").writeText("jpeg")
        if (back) File(folder.root, "files/cards/$cardId/back.jpg").writeText("jpeg")
        cards.create(
            BusinessCardDraft(
                frontOriginal = "cards/$cardId/front_original.jpg",
                frontProcessed = "cards/$cardId/front.jpg",
                backProcessed = if (back) "cards/$cardId/back.jpg" else null,
                capturedAt = 1
            ),
            id = cardId
        )
    }

    private fun run(ocr: OcrService, attempt: Int = 0, id: String = cardId): ListenableWorker.Result = runBlocking { work(ocr, attempt, id) }

    private suspend fun work(ocr: OcrService, attempt: Int = 0, id: String = cardId): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                OcrWorker(appContext, workerParameters, cards, store, ocr, CardFieldExtractor(), OcrQueue(appContext))
        }
        return TestListenableWorkerBuilder<OcrWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(OcrWorker.KEY_CARD_ID to id))
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    private fun status() = runBlocking { cards.get(cardId)!!.ocrStatus }

    @Test
    fun `a readable card ends pending review with its text and proposed fields`() {
        card()
        val ocr = ScriptedOcr(result("NAVALTECH S.L.", "Ana Ruiz", "Directora Comercial", "ana.ruiz@navaltech-demo.es"))
        assertEquals(ListenableWorker.Result.success(), run(ocr))
        val stored = runBlocking { cards.get(cardId)!! }
        assertEquals(OcrStatus.PENDING_REVIEW, stored.ocrStatus)
        assertEquals("scripted", stored.ocrEngine)
        assertTrue(stored.rawOcrText.contains("Directora Comercial"))
        val fields = assertNotNull(ExtractedFields.fromJson(stored.extractedJson))
        assertEquals("ana.ruiz@navaltech-demo.es", fields.value(CardField.EMAIL))
        assertEquals("Ana", fields.value(CardField.FIRST_NAME))
        assertEquals(listOf(File(folder.root, "files/cards/$cardId/front.jpg").canonicalFile), ocr.seen)
        // Nothing else in the queue: the engine is freed.
        assertEquals(1, ocr.released)
    }

    @Test
    fun `a card left in PROCESSING by a crash is read again`() {
        card()
        runBlocking { cards.updateOcr(cardId, OcrStatus.PROCESSING) }
        assertEquals(ListenableWorker.Result.success(), run(ScriptedOcr(result("Ana Ruiz"))))
        assertEquals(OcrStatus.PENDING_REVIEW, status())
    }

    @Test
    fun `both sides are read and their text kept`() {
        card(back = true)
        val ocr = ScriptedOcr(result("Ana Ruiz"), result("Sales Director"))
        run(ocr)
        val stored = runBlocking { cards.get(cardId)!! }
        assertEquals("Ana Ruiz\n\nSales Director", stored.rawOcrText)
        assertEquals(2, ocr.seen.size)
    }

    @Test
    fun `a failure is retried once, then the card waits for manual input with its photo`() {
        card()
        assertEquals(ListenableWorker.Result.retry(), run(ScriptedOcr(null), attempt = 0))
        assertEquals(OcrStatus.PENDING, status())
        assertEquals(ListenableWorker.Result.success(), run(ScriptedOcr(null), attempt = 1))
        assertEquals(OcrStatus.OCR_ERROR, status())
        assertTrue(File(folder.root, "files/cards/$cardId/front.jpg").isFile)
    }

    @Test
    fun `a missing image file is an OCR failure, not a crash`() {
        card()
        File(folder.root, "files/cards/$cardId/front.jpg").delete()
        val ocr = object : OcrService {
            override val engineId = "real-like"

            override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult =
                if (image.file.isFile) OcrResult.EMPTY else throw OcrException("missing ${image.file}")
        }
        assertEquals(ListenableWorker.Result.success(), run(ocr, attempt = 1))
        assertEquals(OcrStatus.OCR_ERROR, status())
    }

    @Test
    fun `cards that are gone or already reviewed are skipped`() {
        assertEquals(ListenableWorker.Result.success(), run(ScriptedOcr(), id = "44444444-4444-4444-8444-444444444444"))
        card()
        runBlocking {
            cards.updateOcr(cardId, OcrStatus.REVIEWED)
        }
        val ocr = ScriptedOcr()
        assertEquals(ListenableWorker.Result.success(), run(ocr))
        assertEquals(OcrStatus.REVIEWED, status())
        assertTrue(ocr.seen.isEmpty())
    }

    @Test
    fun `an attached card is searchable by its OCR text`() {
        card()
        run(ScriptedOcr(result("Ana Ruiz", "Departamento de Compras")))
        runBlocking {
            val contacts = com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository(db, SearchIndexer(db), Clock { 1L }, IdGenerator.Uuid)
            val contact = (contacts.create(ContactDraft("Ana", "Ruiz")) as com.qtekfun.ultimatevisitcards.domain.model.DomainResult.Success).value
            cards.attachToContact(cardId, contact.id)
            assertEquals(listOf("Ruiz"), contacts.search("compras").first().map { it.lastName })
        }
    }

    @Test
    fun `an unexpected engine error ends in manual input, never in an endless reading`() {
        card()
        val broken = object : OcrService {
            var released = 0
            override val engineId = "broken"

            override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult = throw IllegalStateException("native crash")

            override suspend fun release() {
                released++
            }
        }
        assertEquals(ListenableWorker.Result.retry(), run(broken, attempt = 0))
        assertEquals(OcrStatus.PENDING, status())
        assertEquals(ListenableWorker.Result.success(), run(broken, attempt = 1))
        assertEquals(OcrStatus.OCR_ERROR, status())
        // The broken engine is thrown away each time so the next card gets a fresh one.
        assertTrue(broken.released >= 2)
    }

    @Test
    fun `an engine that never answers times out and the card can be filled by hand`() = runTest {
        card()
        val hung = object : OcrService {
            override val engineId = "hung"

            override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult = awaitCancellation()
        }
        assertEquals(ListenableWorker.Result.success(), work(hung, attempt = 1))
        assertEquals(OcrStatus.OCR_ERROR, status())
    }
}

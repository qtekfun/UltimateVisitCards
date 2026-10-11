package com.qtekfun.ultimatevisitcards.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.model.CardQuad
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.NormPoint
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.ocr.processing.CardImageProcessor
import com.qtekfun.ultimatevisitcards.work.OcrQueue
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The normal capture flow end to end: real image processing, real files, real database (in memory). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val appScope = TestScope(dispatcher)
    private lateinit var db: AppDatabase
    private lateinit var store: ImageStore
    private lateinit var cards: RoomBusinessCardRepository
    private val ids = ArrayDeque(listOf("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"))
    private val clock = Clock { 1_767_225_600_000L }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(dispatcher)
            .build()
        store = ImageStore(folder.newFolder("files"), folder.newFolder("cache"), dispatcher)
        cards = RoomBusinessCardRepository(db, SearchIndexer(db), clock, IdGenerator.Uuid)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = CaptureViewModel(
        CardCaptureSaver(store, CardImageProcessor(), cards, OcrQueue(context), dispatcher),
        IdGenerator { ids.removeFirst() },
        clock,
        appScope
    )

    /** A light card rotated a little on a dark table, like the guide asks for. */
    private fun cardPhoto(): Bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.rgb(60, 50, 40))
        canvas.rotate(6f, 600f, 800f)
        canvas.drawRect(160f, 540f, 1040f, 1110f, Paint().apply { color = Color.rgb(235, 235, 230) })
        canvas.drawRect(260f, 700f, 700f, 740f, Paint().apply { color = Color.rgb(30, 30, 30) })
    }

    private fun files(cardId: String) = File(folder.root, "files/cards/$cardId").list()?.sorted().orEmpty()

    @Test
    fun `front only - the card is saved as pending with its straightened image and corners`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        val front = assertNotNull(vm.state.value.front)
        assertEquals(CaptureStep.FRONT_TAKEN, vm.state.value.step)
        assertTrue(front.detected, "the card should be found")
        val ratio = front.preview.width.toDouble() / front.preview.height
        assertTrue(ratio in 1.45..1.65, "ratio $ratio")

        vm.skipBack()
        advanceUntilIdle()
        assertEquals(CaptureStep.SAVED, vm.state.value.step)
        // The screen goes on to review this card.
        assertEquals("11111111-1111-4111-8111-111111111111", vm.state.value.savedCardId)
        val card = assertNotNull(cards.get("11111111-1111-4111-8111-111111111111"))
        assertEquals(OcrStatus.PENDING, card.ocrStatus)
        assertNull(card.contactId)
        assertEquals("cards/${card.id}/front.jpg", card.frontProcessed)
        assertEquals("cards/${card.id}/front_original.jpg", card.frontOriginal)
        assertNull(card.backProcessed)
        assertNotNull(CardQuad.fromJson(card.frontQuad))
        assertEquals(clock.nowMillis(), card.capturedAt)
        assertEquals(listOf("front.jpg", "front_original.jpg"), files(card.id))
        // Its OCR is queued right away (the worker itself is tested in OcrWorkerTest).
        val queued = WorkManager.getInstance(context).getWorkInfosForUniqueWork(OcrQueue.NAME).get()
        assertEquals(1, queued.size)
        assertTrue(OcrQueue.TAG in queued.single().tags)
    }

    @Test
    fun `front and back - both sides are stored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        vm.addBack()
        assertEquals(CardSide.BACK, vm.state.value.currentSide)
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        assertEquals(CaptureStep.BACK_TAKEN, vm.state.value.step)
        vm.saveWithBack()
        advanceUntilIdle()
        val card = assertNotNull(cards.get("11111111-1111-4111-8111-111111111111"))
        assertEquals("cards/${card.id}/back.jpg", card.backProcessed)
        assertNotNull(CardQuad.fromJson(card.backQuad))
        assertEquals(listOf("back.jpg", "back_original.jpg", "front.jpg", "front_original.jpg"), files(card.id))
    }

    @Test
    fun `retaking a side removes its files`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        vm.retake()
        advanceUntilIdle()
        assertEquals(CaptureStep.FRONT, vm.state.value.step)
        assertNull(vm.state.value.front)
        assertEquals(emptyList(), files("11111111-1111-4111-8111-111111111111"))
    }

    @Test
    fun `manual crop replaces the straightened image and the corners`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        vm.openCrop(CardSide.FRONT)
        advanceUntilIdle()
        val crop = assertNotNull(vm.state.value.crop)
        assertEquals(1200 to 1600, crop.original.width to crop.original.height)
        val manual = CardQuad(NormPoint(0.1f, 0.3f), NormPoint(0.9f, 0.3f), NormPoint(0.9f, 0.6f), NormPoint(0.1f, 0.6f))
        vm.applyCrop(manual)
        advanceUntilIdle()
        assertNull(vm.state.value.crop)
        val front = assertNotNull(vm.state.value.front)
        assertEquals(manual, front.quad)
        assertEquals(960 to 480, front.preview.width to front.preview.height)
    }

    @Test
    fun `leaving before saving deletes the photos`() = runTest(dispatcher) {
        // Owned by a store, as on screen: clearing the store is what leaving the screen does.
        val owner = ViewModelStore()
        val vm = ViewModelProvider.create(owner, viewModelFactory { initializer { viewModel() } })[CaptureViewModel::class]
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        assertFalse(files("11111111-1111-4111-8111-111111111111").isEmpty())
        owner.clear()
        advanceUntilIdle()
        assertEquals(emptyList(), files("11111111-1111-4111-8111-111111111111"))
    }

    @Test
    fun `a failed save reports the error and leaves no files`() = runTest(dispatcher) {
        // Another card already uses the id the capture will get.
        cards.create(BusinessCardDraft("x", "y", capturedAt = 1), id = "11111111-1111-4111-8111-111111111111")
        val vm = viewModel()
        vm.onPhoto(cardPhoto())
        advanceUntilIdle()
        vm.skipBack()
        advanceUntilIdle()
        assertEquals(CaptureError.SAVE_FAILED, vm.state.value.error)
        assertEquals(emptyList(), files("11111111-1111-4111-8111-111111111111"))
        vm.dismissError()
        assertNull(vm.state.value.error)
    }
}

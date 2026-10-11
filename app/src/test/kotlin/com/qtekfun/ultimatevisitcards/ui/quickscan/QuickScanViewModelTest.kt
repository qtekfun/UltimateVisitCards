package com.qtekfun.ultimatevisitcards.ui.quickscan

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.qtekfun.ultimatevisitcards.domain.extract.CardFieldExtractor
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.ocr.processing.CardImageProcessor
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import com.qtekfun.ultimatevisitcards.testing.RecordingScheduler
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.ui.capture.CardCaptureSaver
import com.qtekfun.ultimatevisitcards.work.OcrQueue
import com.qtekfun.ultimatevisitcards.work.OcrWorker
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickScanViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var queue: OcrQueue
    private val scheduled = RecordingScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        WorkManagerTestInitHelper.initializeTestWorkManager(data.app, Configuration.Builder().setExecutor { }.build())
        queue = OcrQueue(data.app)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(appScope: TestScope = TestScope(dispatcher)) = QuickScanViewModel(
        CardCaptureSaver(data.store, CardImageProcessor(), data.cards, scheduled, UiPreferences(data.app), dispatcher),
        data.events,
        IdGenerator.Uuid,
        data.clock,
        appScope
    )

    private suspend fun pending() = data.cards.observeAwaitingReview().first()

    @Test
    fun `fifty cards in a row are saved without waiting and all get read`() = runTest(dispatcher) {
        val vm = viewModel()
        var shots = 0
        var maxWaiting = 0
        while (shots < 50) {
            // The camera only shoots when the shutter is enabled; otherwise the background work catches up.
            if (vm.state.value.canShoot) {
                vm.onPhoto(AppTestData.cardPhoto())
                shots++
                maxWaiting = maxOf(maxWaiting, vm.state.value.processing)
            } else {
                advanceUntilIdle()
            }
        }
        advanceUntilIdle()
        assertEquals(50, vm.state.value.saved)
        assertEquals(0, vm.state.value.processing)
        assertTrue(maxWaiting <= QuickScanUiState.MAX_PENDING)
        val cards = pending()
        assertEquals(50, cards.size)
        assertTrue(cards.all { it.ocrStatus == OcrStatus.PENDING && it.contactId == null })
        assertEquals(cards.map { it.id }.toSet(), scheduled.enqueued.toSet())

        // The OCR queue then reads every one of them.
        cards.forEach { assertEquals(ListenableWorker.Result.success(), runOcr(it.id)) }
        assertTrue(pending().all { it.ocrStatus == OcrStatus.PENDING_REVIEW })
    }

    // Suspending, on the test dispatcher: Room runs there too, so blocking it would deadlock.
    private suspend fun runOcr(cardId: String): ListenableWorker.Result {
        val ocr = object : OcrService {
            override val engineId = "scripted"

            override suspend fun recognize(image: OcrImage, hints: OcrHints) = OcrResult("Ana Ruiz", emptyList())
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                OcrWorker(appContext, workerParameters, data.cards, data.store, ocr, CardFieldExtractor(), queue)
        }
        return TestListenableWorkerBuilder<OcrWorker>(
            data.app
        ).setWorkerFactory(factory).setInputData(workDataOf(OcrWorker.KEY_CARD_ID to cardId)).build().doWork()
    }

    @Test
    fun `with backs on two shots make one card and «no back» saves the front alone`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleHasBack()
        vm.onPhoto(AppTestData.cardPhoto())
        assertTrue(vm.state.value.awaitingBack)
        vm.onPhoto(AppTestData.cardPhoto())
        advanceUntilIdle()
        assertEquals(1, vm.state.value.saved)
        assertTrue(pending().single().backProcessed != null)

        vm.onPhoto(AppTestData.cardPhoto())
        vm.skipBack()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.saved)
        assertEquals(1, pending().count { it.backProcessed == null })
    }

    @Test
    fun `the active event goes to every card of the batch`() = runTest(dispatcher) {
        val smm = (data.events.create(EventDraft("SMM 2026")) as DomainResult.Success).value
        val vm = viewModel()
        advanceUntilIdle()
        vm.chooseEvent(smm.id)
        repeat(2) {
            vm.onPhoto(AppTestData.cardPhoto())
            advanceUntilIdle()
        }
        assertTrue(pending().all { it.eventId == smm.id })
    }

    @Test
    fun `finishing saves a front still waiting for its back and then closes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleHasBack()
        vm.onPhoto(AppTestData.cardPhoto())
        vm.finish()
        assertTrue(vm.state.value.finishing)
        assertTrue(!vm.state.value.canShoot)
        advanceUntilIdle()
        assertTrue(vm.state.value.finished)
        assertEquals(1, pending().size)
    }

    @Test
    fun `leaving the screen does not lose a saved front`() = runTest(dispatcher) {
        val owner = ViewModelStore()
        val appScope = TestScope(dispatcher)
        val vm = ViewModelProvider.create(owner, viewModelFactory { initializer { viewModel(appScope) } })[QuickScanViewModel::class]
        vm.toggleHasBack()
        vm.onPhoto(AppTestData.cardPhoto())
        advanceUntilIdle()
        owner.clear()
        advanceUntilIdle()
        assertEquals(1, pending().size)
    }
}

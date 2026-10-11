package com.qtekfun.ultimatevisitcards.ui.queue

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import com.qtekfun.ultimatevisitcards.testing.RecordingScheduler
import com.qtekfun.ultimatevisitcards.work.OcrRecovery
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** The review queue, the OCR retry and the recovery after a forced close. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewQueueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private val scheduled = RecordingScheduler()
    private lateinit var recovery: OcrRecovery

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        recovery = OcrRecovery(data.cards, scheduled)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private suspend fun card(id: String, status: OcrStatus, at: Long): String {
        data.store.saveProcessed(id, com.qtekfun.ultimatevisitcards.domain.model.CardSide.FRONT, AppTestData.cardPhoto())
        data.cards.create(BusinessCardDraft("cards/$id/front_original.jpg", "cards/$id/front.jpg", capturedAt = at), id = id)
        if (status != OcrStatus.PENDING) data.cards.updateOcr(id, status)
        return id
    }

    private fun queued() = scheduled.enqueued.size

    @Test
    fun `cards interrupted mid-OCR go back to the queue at start-up`() = runTest(dispatcher) {
        card("a1111111-1111-4111-8111-111111111111", OcrStatus.PROCESSING, 1)
        card("b2222222-2222-4222-8222-222222222222", OcrStatus.PENDING, 2)
        card("c3333333-3333-4333-8333-333333333333", OcrStatus.PENDING_REVIEW, 3)
        assertEquals(2, recovery.resume())
        val statuses = data.cards.observeAwaitingReview().first().associate { it.id.take(1) to it.ocrStatus }
        assertEquals(mapOf("a" to OcrStatus.PENDING, "b" to OcrStatus.PENDING, "c" to OcrStatus.PENDING_REVIEW), statuses)
        assertEquals(2, queued())
    }

    @Test
    fun `the queue lists pending cards oldest first, retries failed ones and discards bad shots`() = runTest(dispatcher) {
        val vm = ReviewQueueViewModel(data.cards, data.store, recovery)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertIs<QueueUiState.Empty>(vm.state.value)

        val failed = card("d4444444-4444-4444-8444-444444444444", OcrStatus.OCR_ERROR, 5)
        val ready = card("e5555555-5555-4555-8555-555555555555", OcrStatus.PENDING_REVIEW, 4)
        advanceUntilIdle()
        val content = assertIs<QueueUiState.Content>(vm.state.value)
        assertEquals(listOf(ready, failed), content.items.map { it.card.id })
        assertTrue(content.items.all { it.thumbnail?.isFile == true })

        vm.retry(failed)
        advanceUntilIdle()
        assertEquals(OcrStatus.PENDING, data.cards.get(failed)!!.ocrStatus)
        assertEquals(1, queued())

        vm.discard(ready)
        advanceUntilIdle()
        assertEquals(null, data.cards.get(ready))
        assertFalse(File(folder.root, "files/cards/$ready").exists())
        assertEquals(listOf(failed), assertIs<QueueUiState.Content>(vm.state.value).items.map { it.card.id })
    }
}

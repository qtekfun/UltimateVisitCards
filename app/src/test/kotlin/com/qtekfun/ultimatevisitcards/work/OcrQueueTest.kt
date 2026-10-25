package com.qtekfun.ultimatevisitcards.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.extract.CardFieldExtractor
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Cards are read in the app process, one at a time and in order, as soon as they are queued. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrQueueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData

    /** Records the order of the readings and checks that two never overlap. */
    private class RecordingOcr : OcrService {
        val read = mutableListOf<String>()
        var running = 0
        var maxRunning = 0
        var released = 0
        var failNext = false

        override val engineId = "recording"

        override suspend fun recognize(image: OcrImage, hints: OcrHints): OcrResult {
            running++
            maxRunning = maxOf(maxRunning, running)
            try {
                // A reading takes a while, so overlapping readings would show up in maxRunning.
                delay(1_000)
                if (failNext) {
                    failNext = false
                    throw IllegalStateException("engine crash")
                }
                read += image.file.parentFile!!.name
                return OcrResult("Ana Ruiz", emptyList())
            } finally {
                running--
            }
        }

        override suspend fun release() {
            released++
        }
    }

    @Before
    fun setUp() {
        data = AppTestData(dispatcher, folder.root)
    }

    @After
    fun tearDown() = data.close()

    private suspend fun card(): String {
        val id = IdGenerator.Uuid.newId()
        data.store.saveProcessed(id, CardSide.FRONT, AppTestData.cardPhoto())
        data.cards.create(BusinessCardDraft("cards/$id/front_original.jpg", "cards/$id/front.jpg", capturedAt = 1), id = id)
        return id
    }

    /** The queue runs in backgroundScope, whose delays advanceUntilIdle does not wait for. */
    private fun TestScope.drain() {
        advanceTimeBy(30_000)
        runCurrent()
    }

    private suspend fun status(id: String) = data.cards.get(id)!!.ocrStatus

    @Test
    fun `queued cards are read one after another, in order, and the engine is freed at the end`() = runTest(dispatcher) {
        val ocr = RecordingOcr()
        val queue = OcrQueue(OcrCardReader(data.cards, data.store, ocr, CardFieldExtractor()), ocr, backgroundScope)
        val ids = List(3) { card() }
        ids.forEach(queue::enqueue)
        drain()
        assertEquals(ids, ocr.read)
        assertEquals(1, ocr.maxRunning)
        assertTrue(ids.all { status(it) == OcrStatus.PENDING_REVIEW })
        assertEquals(1, ocr.released)
    }

    @Test
    fun `a card the engine cannot read does not stop the ones behind it`() = runTest(dispatcher) {
        val ocr = RecordingOcr().apply { failNext = true }
        val queue = OcrQueue(OcrCardReader(data.cards, data.store, ocr, CardFieldExtractor()), ocr, backgroundScope)
        val first = card()
        val second = card()
        queue.enqueue(first)
        queue.enqueue(second)
        drain()
        // The first card fails once and is read on its second attempt; the next one follows.
        assertEquals(listOf(first, second), ocr.read)
        assertEquals(OcrStatus.PENDING_REVIEW, status(first))
        assertEquals(OcrStatus.PENDING_REVIEW, status(second))
    }
}

package com.qtekfun.ultimatevisitcards.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** One chain, one card at a time: later cards wait (BLOCKED) behind the one being read. */
@RunWith(AndroidJUnit4::class)
class OcrQueueTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Stands for an OCR that takes a while: it never finishes during the test. */
    class Busy(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = awaitCancellation()
    }

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = Busy(appContext, workerParameters)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build()
        )
    }

    @Test
    fun `cards are chained one after another`() {
        val queue = OcrQueue(context)
        repeat(3) { queue.enqueue("card-$it") }
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(OcrQueue.NAME).get()
        assertEquals(3, infos.size)
        assertEquals(1, infos.count { it.state == WorkInfo.State.RUNNING })
        assertEquals(2, infos.count { it.state == WorkInfo.State.BLOCKED })
        assertTrue(infos.all { OcrQueue.TAG in it.tags })
        // While cards wait behind the running one, the queue is not drained.
        assertEquals(false, runBlocking { queue.isDrained() })
    }
}

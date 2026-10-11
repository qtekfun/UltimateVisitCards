package com.qtekfun.ultimatevisitcards.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The OCR queue (docs/04 §4): a single chain of [OcrWorker]s, one card each, so recognition never runs in
 * parallel (Tesseract is memory hungry). `APPEND_OR_REPLACE` keeps the chain going even after a failed card.
 */
@Singleton
class OcrQueue @Inject constructor(@ApplicationContext private val context: Context) : OcrScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    override fun enqueue(cardId: String) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(OcrWorker.KEY_CARD_ID to cardId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** True when no other card is waiting behind the one running now. */
    suspend fun isDrained(): Boolean = workManager.getWorkInfosForUniqueWorkFlow(NAME).first()
        .none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    companion object {
        const val NAME = "ocr-queue"
        const val TAG = "ocr"
    }
}

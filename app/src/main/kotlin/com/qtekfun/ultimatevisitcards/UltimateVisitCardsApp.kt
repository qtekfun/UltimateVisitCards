package com.qtekfun.ultimatevisitcards

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexHealth
import com.qtekfun.ultimatevisitcards.di.ApplicationScope
import com.qtekfun.ultimatevisitcards.work.OcrRecovery
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** WorkManager is initialised on demand with Hilt's worker factory (the default initializer is removed in the manifest). */
@HiltAndroidApp
class UltimateVisitCardsApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var ocrRecovery: OcrRecovery

    @Inject
    lateinit var searchIndexHealth: SearchIndexHealth

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // The search index is derived data: rebuilt if it does not match the contacts (e.g. after a migration).
            searchIndexHealth.ensure()
            // Cards interrupted by a forced close go back to the OCR queue.
            ocrRecovery.resume()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

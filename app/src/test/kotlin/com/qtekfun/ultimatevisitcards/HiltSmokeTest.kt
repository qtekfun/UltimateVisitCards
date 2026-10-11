package com.qtekfun.ultimatevisitcards

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.di.DefaultDispatcher
import com.qtekfun.ultimatevisitcards.di.IoDispatcher
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrEngineInfo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The singleton graph builds and each flavour binds its own OCR engine. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltSmokeTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var ocrEngine: OcrEngineInfo

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    @DefaultDispatcher
    lateinit var defaultDispatcher: CoroutineDispatcher

    @Before
    fun inject() {
        hilt.inject()
    }

    @Test
    fun bindsTheFlavourOcrEngine() {
        assertEquals(ExpectedFlavour.OCR_ENGINE, ocrEngine.engineId)
    }

    @Test
    fun bindsTheDispatchers() {
        assertSame(Dispatchers.IO, ioDispatcher)
        assertSame(Dispatchers.Default, defaultDispatcher)
    }
}

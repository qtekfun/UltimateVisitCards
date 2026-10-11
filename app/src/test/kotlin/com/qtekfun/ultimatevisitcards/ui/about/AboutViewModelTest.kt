package com.qtekfun.ultimatevisitcards.ui.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.R
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrHints
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrImage
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrService
import com.qtekfun.ultimatevisitcards.settings.AppInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AboutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ocr(id: String) = object : OcrService {
        override val engineId = id

        override suspend fun recognize(image: OcrImage, hints: OcrHints) = OcrResult.EMPTY
    }

    private fun viewModel(engine: String) = AboutViewModel(app, AppInfo(app), ocr(engine), dispatcher)

    @Test
    fun `play build shows ML Kit and its licences only`() = runTest(dispatcher) {
        val vm = viewModel(OcrService.MLKIT)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(R.string.about_engine_mlkit, state.engine)
        assertFalse(state.licensesFailed)
        val names = state.licenses.map { it.name }
        assertTrue("ML Kit Text Recognition" in names)
        assertTrue(names.none { it.contains("Tesseract") || it.contains("tessdata") })
        assertTrue(listOf("Room and AndroidX SQLite", "Coil", "zip4j", "kotlinx.coroutines").all { it in names })
        assertTrue(state.licenses.all { it.license.isNotBlank() && it.url.startsWith("http") })
    }

    @Test
    fun `foss build shows Tesseract and tessdata, not ML Kit`() = runTest(dispatcher) {
        val vm = viewModel(OcrService.TESSERACT)
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(R.string.about_engine_tesseract, state.engine)
        val names = state.licenses.map { it.name }
        assertTrue("Tesseract4Android" in names)
        assertTrue(names.any { it.startsWith("tessdata_fast") })
        assertTrue(names.none { it.startsWith("ML Kit") })
    }

    @Test
    fun `parses the licence list`() {
        val parsed = AboutViewModel.parseLicenses(
            """[{"name": "b", "license": "MIT", "url": "https://b"}, {"name": "A", "license": "Apache-2.0", "url": "https://a", "flavour": "foss"}]"""
        )
        assertEquals(listOf(ThirdPartyLicense("A", "Apache-2.0", "https://a", "foss"), ThirdPartyLicense("b", "MIT", "https://b")), parsed)
        assertNull(AboutViewModel.engineLabel("unknown"))
    }
}

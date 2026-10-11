package com.qtekfun.ultimatevisitcards.ui.settings

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.settings.AppLock
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** Settings → security, capture and maintenance. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreferencesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var contacts: RoomContactRepository
    private lateinit var preferences: UiPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        contacts = RoomContactRepository(data.db, data.indexer, data.clock, IdGenerator.Uuid)
        preferences = UiPreferences(data.app)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = PreferencesViewModel(preferences, AppLock(data.app, preferences), contacts, data.store)

    @Test
    fun `switching originals off deletes the stored ones and keeps the straightened cards`() = runTest(dispatcher) {
        val cardId = "cccccccc-0000-4000-8000-000000000001"
        val photo = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        val original = data.store.saveOriginal(cardId, CardSide.FRONT, photo)
        val processed = data.store.saveProcessed(cardId, CardSide.FRONT, photo)
        val vm = viewModel()

        vm.keepOriginalsOff()
        advanceUntilIdle()
        assertFalse(vm.keepOriginals.value)
        assertEquals(MaintenanceResult.ORIGINALS_DELETED, vm.result.value)
        assertFalse(data.store.file(original).exists())
        assertTrue(data.store.file(processed).exists())

        vm.resultShown()
        assertNull(vm.result.value)
        vm.keepOriginalsOn()
        assertTrue(preferences.keepOriginals.value)
    }

    @Test
    fun `rebuilding the search index brings back a lost index`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Luca", "Bianchi", jobTitle = "Responsabile Vendite"))
        data.db.searchDao().deleteAll()
        assertEquals(0, contacts.observeList(ContactFilter(text = "vendite")).first().size)

        val vm = viewModel()
        vm.reindex()
        advanceUntilIdle()
        assertEquals(MaintenanceResult.REINDEXED, vm.result.value)
        assertFalse(vm.busy.value)
        assertEquals(1, contacts.observeList(ContactFilter(text = "vendite")).first().size)
    }

    @Test
    fun `the lock is only turned on when the phone has a screen lock`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setLock(true)
        assertEquals(vm.lockAvailable, vm.lockEnabled.value)
        vm.setLock(false)
        assertFalse(vm.lockEnabled.value)
    }

    @Test
    fun `hide in recents and the threshold are saved`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setSecureScreen(true)
        vm.setThreshold(0.8f)
        assertTrue(UiPreferences(data.app).secureScreen.value)
        assertEquals(0.8f, UiPreferences(data.app).confidenceThreshold.value)
    }
}

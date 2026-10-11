package com.qtekfun.ultimatevisitcards.ui.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.backup.BackupExporter
import com.qtekfun.ultimatevisitcards.data.backup.BackupRestorer
import com.qtekfun.ultimatevisitcards.data.backup.RestoreMode
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.settings.AppInfo
import com.qtekfun.ultimatevisitcards.settings.CardsView
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import com.qtekfun.ultimatevisitcards.ui.common.Documents
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

/** Settings → backup and restore, end to end through files (as the SAF picker would give them). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupRestoreViewModelTest {
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

    private fun backup() = BackupViewModel(BackupExporter(data.db, data.store, data.clock, dispatcher), Documents(data.app), preferences, AppInfo(data.app))

    private fun restore() = RestoreViewModel(BackupRestorer(data.db, data.store, data.indexer, dispatcher), Documents(data.app), preferences)

    private suspend fun names() = contacts.observeAll().first().map { it.lastName }.sorted()

    @Test
    fun `export, change the data, restore combining - and with a password`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz"))
        contacts.create(ContactDraft("Luis", "Gil"))
        preferences.setCardsView(CardsView.LIST)
        val file = folder.newFile("backup.zip")

        val exportVm = backup()
        exportVm.startExport()
        assertEquals(ExportStep.Options, exportVm.export.value)
        exportVm.chooseOptions(ExportChoice(includeOriginals = true, password = "secreto123".toCharArray()))
        exportVm.exportTo(Uri.fromFile(file))
        advanceUntilIdle()
        assertEquals(2, assertIs<ExportStep.Done>(exportVm.export.value).manifest.counts.contacts)
        assertTrue(file.length() > 0)

        // Meanwhile one contact is deleted and the view changed.
        contacts.delete(contacts.observeAll().first().first { it.lastName == "Gil" }.id)
        preferences.setCardsView(CardsView.GRID)

        val vm = restore()
        vm.open(Uri.fromFile(file))
        advanceUntilIdle()
        assertEquals(RestoreStep.Password(wrong = false), vm.step.value)
        vm.submitPassword("mala".toCharArray())
        advanceUntilIdle()
        assertEquals(RestoreStep.Password(wrong = true), vm.step.value)
        vm.submitPassword("secreto123".toCharArray())
        advanceUntilIdle()
        assertEquals(2, assertIs<RestoreStep.Summary>(vm.step.value).counts.contacts)
        vm.choose(RestoreMode.MERGE)
        advanceUntilIdle()
        val report = assertIs<RestoreStep.Done>(vm.step.value).report
        assertEquals(1, report.added.contacts)
        assertEquals(listOf("Gil", "Ruiz"), names())
        assertEquals(CardsView.LIST, preferences.cardsView.value)
    }

    @Test
    fun `replace everything asks first`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz"))
        val file = folder.newFile("backup.zip")
        val exportVm = backup()
        exportVm.startExport()
        exportVm.chooseOptions(ExportChoice())
        exportVm.exportTo(Uri.fromFile(file))
        advanceUntilIdle()
        contacts.create(ContactDraft("Nuevo", "Contacto"))

        val vm = restore()
        vm.open(Uri.fromFile(file))
        advanceUntilIdle()
        vm.choose(RestoreMode.REPLACE)
        assertIs<RestoreStep.ConfirmReplace>(vm.step.value)
        vm.confirmReplace()
        advanceUntilIdle()
        assertIs<RestoreStep.Done>(vm.step.value)
        assertEquals(listOf("Ruiz"), names())
    }

    @Test
    fun `a file that is not a backup is reported and changes nothing`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz"))
        val file = folder.newFile("notes.zip").apply { writeText("hello") }
        val vm = restore()
        vm.open(Uri.fromFile(file))
        advanceUntilIdle()
        val problem = assertIs<RestoreStep.Problem>(vm.step.value).problem
        assertTrue(problem == RestoreProblem.NOT_A_BACKUP || problem == RestoreProblem.CORRUPT)
        assertEquals(listOf("Ruiz"), names())
        vm.close()
        assertEquals(RestoreStep.Idle, vm.step.value)
    }

    @Test
    fun `a cancelled export deletes the partial file`() = runTest(dispatcher) {
        repeat(3) { contacts.create(ContactDraft("C$it", "X$it")) }
        val file = folder.newFile("partial.zip")
        val vm = backup()
        vm.startExport()
        vm.chooseOptions(ExportChoice())
        vm.exportTo(Uri.fromFile(file))
        vm.cancelExport()
        advanceUntilIdle()
        assertEquals(ExportStep.Failed(ExportFailure.CANCELLED), vm.export.value)
        assertTrue(!file.exists())
    }
}

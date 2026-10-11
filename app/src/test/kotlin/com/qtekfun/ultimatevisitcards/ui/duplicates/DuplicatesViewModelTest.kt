package com.qtekfun.ultimatevisitcards.ui.duplicates

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateDetector
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateScanner
import com.qtekfun.ultimatevisitcards.domain.dedupe.MatchReason
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuplicatesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var contacts: RoomContactRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        contacts = RoomContactRepository(data.db, data.indexer, data.clock, IdGenerator.Uuid)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private fun <T> DomainResult<T>.value() = (this as DomainResult.Success).value

    private fun viewModel() = DuplicatesViewModel(contacts, DuplicateScanner(DuplicateDetector()), dispatcher)

    @Test
    fun `a single contact has no duplicates`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Mary", "Jones"))
        val vm = viewModel()
        assertEquals(DuplicatesUiState.Loading, vm.state.value)
        advanceUntilIdle()
        assertEquals(DuplicatesUiState.Empty, vm.state.value)
    }

    @Test
    fun `pairs best first, keeping the older contact`() = runTest(dispatcher) {
        val older = contacts.create(ContactDraft("John", "Smith")).value()
        val newer = contacts.create(ContactDraft("Johnny", "S.", email = "ana@abc-demo.com")).value()
        val ana = contacts.create(ContactDraft("Ana", "Ruiz", email = "ANA@abc-demo.com ")).value()
        val john = contacts.create(ContactDraft("John", "Smith")).value()
        contacts.create(ContactDraft("Mary", "Jones"))
        val vm = viewModel()
        advanceUntilIdle()
        val rows = assertIs<DuplicatesUiState.Content>(vm.state.value).rows
        assertEquals(
            listOf(Triple(newer.id, ana.id, MatchReason.EMAIL), Triple(older.id, john.id, MatchReason.NAME)),
            rows.map { Triple(it.keep.contact.id, it.other.contact.id, it.reason) }
        )
    }

    @Test
    fun `refresh drops pairs that were merged`() = runTest(dispatcher) {
        contacts.create(ContactDraft("John", "Smith")).value()
        val copy = contacts.create(ContactDraft("John", "Smith")).value()
        val vm = viewModel()
        advanceUntilIdle()
        assertIs<DuplicatesUiState.Content>(vm.state.value)
        contacts.delete(copy.id)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(DuplicatesUiState.Empty, vm.state.value)
    }
}

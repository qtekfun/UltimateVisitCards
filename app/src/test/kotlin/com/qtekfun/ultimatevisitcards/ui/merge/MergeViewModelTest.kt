package com.qtekfun.ultimatevisitcards.ui.merge

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomTransactionRunner
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeContactsUseCase
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MergeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var contacts: RoomContactRepository
    private lateinit var companies: RoomCompanyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        contacts = RoomContactRepository(data.db, data.indexer, data.clock, IdGenerator.Uuid)
        companies = RoomCompanyRepository(data.db, data.indexer, CompanyNameNormalizer.fromBundledDictionary(), data.clock, IdGenerator.Uuid)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private fun <T> DomainResult<T>.value() = (this as DomainResult.Success).value

    @Test
    fun `pick another contact, choose values, merge and leave the undo for the sheet`() = runTest(dispatcher) {
        val keep = contacts.create(ContactDraft("John", "Smith", jobTitle = "Manager")).value()
        val other = contacts.create(ContactDraft("John", "Smith", jobTitle = "Director", email = "john@abc-demo.com")).value()
        contacts.create(ContactDraft("Mary", "Jones"))
        val holder = MergeUndoHolder()
        val vm = MergeViewModel(
            SavedStateHandle(mapOf(MergeViewModel.ARG_KEEP_ID to keep.id)),
            contacts,
            companies,
            data.cards,
            MergeContactsUseCase(contacts, companies, data.cards, RoomTransactionRunner(data.db), data.clock),
            holder
        )
        advanceUntilIdle()
        // The contact itself is never offered.
        assertTrue(vm.state.value.candidates.none { it.contact.id == keep.id })
        vm.onQuery("smith")
        advanceUntilIdle()
        assertEquals(listOf(other.id), vm.state.value.candidates.map { it.contact.id })

        vm.pick(other)
        advanceUntilIdle()
        val plan = assertNotNull(vm.state.value.plan)
        assertEquals(listOf(MergeField.JOB_TITLE), plan.conflicts)
        vm.choose(MergeField.JOB_TITLE, MergeSource.EXISTING)
        vm.confirm()
        advanceUntilIdle()

        assertTrue(vm.state.value.done)
        val merged = contacts.get(keep.id)!!
        assertEquals("Manager", merged.jobTitle)
        assertEquals("john@abc-demo.com", merged.email)
        assertNull(contacts.get(other.id))
        assertEquals(keep.id, holder.last.value?.keptBefore?.id)
    }

    @Test
    fun `coming from possible duplicates, the comparison opens straight away`() = runTest(dispatcher) {
        val keep = contacts.create(ContactDraft("John", "Smith", jobTitle = "Manager")).value()
        val other = contacts.create(ContactDraft("John", "Smith", jobTitle = "Director")).value()
        val vm = MergeViewModel(
            SavedStateHandle(mapOf(MergeViewModel.ARG_KEEP_ID to keep.id, MergeViewModel.ARG_OTHER_ID to other.id)),
            contacts,
            companies,
            data.cards,
            MergeContactsUseCase(contacts, companies, data.cards, RoomTransactionRunner(data.db), data.clock),
            MergeUndoHolder()
        )
        advanceUntilIdle()
        assertEquals(other.id, vm.state.value.other?.id)
        assertEquals(listOf(MergeField.JOB_TITLE), assertNotNull(vm.state.value.plan).conflicts)
    }
}

package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ContactDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var contacts: RoomContactRepository
    private lateinit var companies: RoomCompanyRepository
    private lateinit var sectors: RoomSectorRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = AppTestData(dispatcher, folder.root)
        contacts = RoomContactRepository(data.db, data.indexer, data.clock, IdGenerator.Uuid)
        companies = RoomCompanyRepository(data.db, data.indexer, CompanyNameNormalizer.fromBundledDictionary(), data.clock, IdGenerator.Uuid)
        sectors = RoomSectorRepository(data.db, data.indexer, data.clock, IdGenerator.Uuid)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    private fun <T> DomainResult<T>.value() = (this as DomainResult.Success).value

    private fun viewModel(id: String) = ContactDetailViewModel(
        SavedStateHandle(mapOf(ContactDetailViewModel.ARG_CONTACT_ID to id)),
        contacts,
        companies,
        sectors,
        data.cards,
        data.events,
        data.store
    )

    @Test
    fun `shows the contact with company, inherited country, events and cards`() = runTest(dispatcher) {
        val sector = sectors.create("Astillero").value()
        val company = companies.create(CompanyDraft("Example Heavy Industries", sector.id, "South Korea")).value()
        val contact = contacts.create(ContactDraft("Ji-ho", "Park", companyId = company.id)).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val card = data.cards.create(BusinessCardDraft("o", "p", eventId = smm.id, capturedAt = 1)).value()
        data.cards.attachToContact(card.id, contact.id)

        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val state = assertIs<ContactDetailUiState.Content>(vm.state.value)
        assertEquals("Example Heavy Industries", state.company?.name)
        assertEquals("Astillero", state.sectorName)
        assertEquals("South Korea", state.country)
        assertEquals(listOf("SMM 2026"), state.events.map { it.name })
        assertEquals(listOf(card.id), state.cards.map { it.card.id })
    }

    @Test
    fun `a deleted contact is reported missing`() = runTest(dispatcher) {
        val contact = contacts.create(ContactDraft("Ana", "Ruiz")).value()
        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertIs<ContactDetailUiState.Content>(vm.state.value)
        contacts.delete(contact.id)
        advanceUntilIdle()
        assertIs<ContactDetailUiState.Missing>(vm.state.value)
    }
}

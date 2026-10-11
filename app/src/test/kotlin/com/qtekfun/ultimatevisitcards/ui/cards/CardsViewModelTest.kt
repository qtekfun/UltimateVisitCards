package com.qtekfun.ultimatevisitcards.ui.cards

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.export.VcfExporter
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.settings.CardsView
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import com.qtekfun.ultimatevisitcards.ui.common.Documents
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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
class CardsViewModelTest {
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

    private fun viewModel(query: String? = null) = CardsViewModel(
        SavedStateHandle(listOfNotNull(query?.let { CardsViewModel.ARG_QUERY to it }).toMap()),
        contacts,
        data.cards,
        companies,
        data.events,
        sectors,
        data.store,
        UiPreferences(data.app),
        VcfExporter(data.db, dispatcher),
        Documents(data.app),
        dispatcher
    )

    private fun <T> DomainResult<T>.value() = (this as DomainResult.Success).value

    @Test
    fun `loading, then contacts with stats, then search and filters`() = runTest(dispatcher) {
        val sector = sectors.create("Astillero").value()
        val company = companies.create(CompanyDraft("Example Heavy Industries", sector.id, "South Korea")).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val park = contacts.create(ContactDraft("Ji-ho", "Park", companyId = company.id)).value()
        contacts.addToEvent(park.id, smm.id)
        contacts.create(ContactDraft("Ana", "Ruiz", country = "Spain"))

        val vm = viewModel()
        assertIs<CardsUiState.Loading>(vm.state.value)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        var state = assertIs<CardsUiState.Content>(vm.state.value)
        assertEquals(listOf("Ruiz", "Park"), state.items.map { it.item.contact.lastName })
        assertEquals(2, state.stats.contacts)
        assertEquals(listOf("South Korea", "Spain"), state.options.countries)

        vm.search("park")
        advanceUntilIdle()
        assertEquals(listOf("Park"), assertIs<CardsUiState.Content>(vm.state.value).items.map { it.item.contact.lastName })

        vm.search("")
        vm.applyFilter(ContactFilter(eventId = smm.id, sectorId = sector.id, country = "South Korea"))
        advanceUntilIdle()
        state = assertIs<CardsUiState.Content>(vm.state.value)
        assertEquals(listOf("Park"), state.items.map { it.item.contact.lastName })
        assertEquals(3, state.filter.activeFilters)

        vm.clearFilters()
        advanceUntilIdle()
        assertEquals(2, assertIs<CardsUiState.Content>(vm.state.value).items.size)
    }

    @Test
    fun `a global search opened from an event starts with its text`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz"))
        contacts.create(ContactDraft("Luis", "Gil"))
        val vm = viewModel(query = "gil")
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val state = assertIs<CardsUiState.Content>(vm.state.value)
        assertEquals("gil", state.filter.text)
        assertEquals(listOf("Gil"), state.items.map { it.item.contact.lastName })
    }

    @Test
    fun `the listed contacts are exported as one vcf`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz", email = "ana@example-demo.es"))
        contacts.create(ContactDraft("Luis", "Gil"))
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.search("ruiz")
        advanceUntilIdle()
        val file = folder.newFile("out.vcf")
        vm.exportVcf(android.net.Uri.fromFile(file))
        advanceUntilIdle()
        assertEquals(ExportResult.Done(1), vm.exported.value)
        val text = file.readText()
        assertEquals(1, Regex("BEGIN:VCARD").findAll(text).count())
        assertTrue(text.contains("FN:Ana Ruiz"))
        vm.exportShown()
        assertEquals(null, vm.exported.value)
    }

    @Test
    fun `the view choice is remembered`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.setView(CardsView.LIST)
        advanceUntilIdle()
        assertEquals(CardsView.LIST, assertIs<CardsUiState.Content>(vm.state.value).view)
        assertEquals(CardsView.LIST, UiPreferences(data.app).cardsView.value)
    }
}

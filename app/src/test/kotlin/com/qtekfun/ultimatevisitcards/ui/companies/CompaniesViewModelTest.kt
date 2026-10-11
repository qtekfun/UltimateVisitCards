package com.qtekfun.ultimatevisitcards.ui.companies

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.CompanySummary
import com.qtekfun.ultimatevisitcards.domain.model.ContactFilter
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.testing.CatalogTestData
import com.qtekfun.ultimatevisitcards.testing.orFail
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import com.qtekfun.ultimatevisitcards.ui.events.EditError
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class CompaniesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: CatalogTestData

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        data = CatalogTestData(dispatcher)
    }

    @After
    fun tearDown() {
        data.close()
        Dispatchers.resetMain()
    }

    @Suppress("UNCHECKED_CAST")
    private fun LoadState<CompanySummary>.names() = (this as LoadState.Data<CompanySummary>).items.map { it.company.name }

    @Test
    fun `the list shows sector, country and contacts, and filters by any of them`() = runTest(dispatcher) {
        val vm = CompaniesViewModel(data.companies)
        backgroundScope.launch { vm.state.collect {} }
        assertIs<LoadState.Loading>(vm.state.value)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.names())

        val shipyard = data.sectors.create("Astillero").orFail()
        val navantia = data.companies.create(CompanyDraft("Navantia Ficticia", shipyard.id, "Spain")).orFail()
        data.companies.create(CompanyDraft("Sample Lines", country = "South Korea")).orFail()
        data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = navantia.id)).orFail()
        advanceUntilIdle()
        val first = (vm.state.value as LoadState.Data<CompanySummary>).items.first()
        assertEquals("Astillero" to 1, first.sectorName to first.contactCount)

        vm.onFilter("astill")
        advanceUntilIdle()
        assertEquals(listOf("Navantia Ficticia"), vm.state.value.names())
        vm.onFilter("KOREA")
        advanceUntilIdle()
        assertEquals(listOf("Sample Lines"), vm.state.value.names())
        vm.onFilter("nothing")
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.names())
    }

    @Test
    fun `the detail lists its contacts, edits with conflict check and deletes`() = runTest(dispatcher) {
        val shipyard = data.sectors.create("Astillero").orFail()
        val abc = data.companies.create(CompanyDraft("ABC Marine")).orFail()
        data.companies.create(CompanyDraft("XYZ Shipping")).orFail()
        data.contacts.create(ContactDraft("Ana", "Ruiz", companyId = abc.id)).orFail()

        val vm = CompanyDetailViewModel(SavedStateHandle(mapOf(CompanyDetailViewModel.ARG_COMPANY_ID to abc.id)), data.companies, data.contacts, data.sectors)
        advanceUntilIdle()
        assertEquals("ABC Marine", vm.state.value.company?.name)
        assertEquals(listOf("Ruiz"), vm.state.value.contacts.map { it.lastName })
        assertEquals(listOf("Astillero"), vm.state.value.sectors.map { it.name })

        vm.startEdit()
        vm.save(CompanyEdit("XYZ Shipping S.L.", null, "", "", ""))
        advanceUntilIdle()
        assertEquals(EditError.NAME_TAKEN, vm.state.value.editor?.error)
        vm.save(CompanyEdit(" ", null, "", "", ""))
        advanceUntilIdle()
        assertEquals(EditError.NAME_REQUIRED, vm.state.value.editor?.error)

        vm.save(CompanyEdit("ABC Marine Services", shipyard.id, "Spain", "www.abc-demo.es", "Cliente"))
        advanceUntilIdle()
        assertNull(vm.state.value.editor)
        assertEquals("Astillero", vm.state.value.sectorName)
        assertEquals("ABC Marine Services", data.companies.get(abc.id)?.name)
        assertEquals("www.abc-demo.es", data.companies.get(abc.id)?.website)

        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.state.value.deleted)
        assertNull(data.companies.get(abc.id))
        // The contact stays, without a company.
        assertNull(data.contacts.observeList(ContactFilter()).first().single().contact.companyId)
    }

    @Test
    fun `a missing company is reported`() = runTest(dispatcher) {
        val vm = CompanyDetailViewModel(SavedStateHandle(mapOf(CompanyDetailViewModel.ARG_COMPANY_ID to "nope")), data.companies, data.contacts, data.sectors)
        advanceUntilIdle()
        assertTrue(vm.state.value.missing)
    }
}

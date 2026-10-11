package com.qtekfun.ultimatevisitcards.ui.sectors

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.SectorSummary
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.testing.CatalogTestData
import com.qtekfun.ultimatevisitcards.testing.orFail
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SectorsViewModelTest {
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
    private fun LoadState<SectorSummary>.items() = (this as LoadState.Data<SectorSummary>).items

    @Test
    fun `create, rename and delete with validation`() = runTest(dispatcher) {
        val vm = SectorsViewModel(data.sectors)
        backgroundScope.launch { vm.state.collect {} }
        assertIs<LoadState.Loading>(vm.state.value)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.items())

        vm.startCreate()
        vm.save("Electrónica")
        advanceUntilIdle()
        assertNull(vm.editor.value)
        val electronics = vm.state.value.items().single().sector

        // The same name, ignoring case and accents, is refused.
        vm.startCreate()
        vm.save("ELECTRONICA")
        advanceUntilIdle()
        assertEquals(SectorError.NAME_TAKEN, vm.editor.value?.error)
        vm.cancel()

        vm.startRename(electronics)
        vm.save(" ")
        advanceUntilIdle()
        assertEquals(SectorError.NAME_REQUIRED, vm.editor.value?.error)
        vm.save("Electrónica naval")
        advanceUntilIdle()
        assertNull(vm.editor.value)
        assertEquals(listOf("Electrónica naval"), vm.state.value.items().map { it.sector.name })

        val company = data.companies.create(CompanyDraft("ABC Marine", sectorId = electronics.id)).orFail()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items().single().companyCount)

        vm.delete(electronics.id)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.items())
        // The company stays, without a sector.
        assertNull(data.companies.get(company.id)?.sectorId)
    }
}

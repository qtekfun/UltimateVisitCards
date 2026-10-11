package com.qtekfun.ultimatevisitcards.ui.events

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.EventSummary
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.testing.CatalogTestData
import com.qtekfun.ultimatevisitcards.testing.orFail
import com.qtekfun.ultimatevisitcards.ui.common.LoadState
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
class EventsViewModelTest {
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

    private fun detail(eventId: String) = EventDetailViewModel(SavedStateHandle(mapOf(EventDetailViewModel.ARG_EVENT_ID to eventId)), data.events, data.contacts)

    @Test
    fun `the list goes from loading to empty to content and creates events`() = runTest(dispatcher) {
        val vm = EventsViewModel(data.events)
        backgroundScope.launch { vm.state.collect {} }
        assertIs<LoadState.Loading>(vm.state.value)
        advanceUntilIdle()
        assertEquals(LoadState.Data(emptyList()), vm.state.value)

        vm.startCreate()
        vm.create(EventDraft(name = " "))
        advanceUntilIdle()
        assertEquals(EditError.NAME_REQUIRED, vm.editor.value?.error)

        vm.create(EventDraft(name = "SMM 2026", location = "Hamburg", startDate = 1_000, endDate = 2_000))
        advanceUntilIdle()
        assertNull(vm.editor.value)
        @Suppress("UNCHECKED_CAST")
        val summary = (vm.state.value as LoadState.Data<EventSummary>).items.single()
        assertEquals("SMM 2026", summary.event.name)
        assertEquals("Hamburg", summary.event.location)
        assertEquals(0, summary.contactCount)
    }

    @Test
    fun `the detail searches only within the event, edits and deletes it`() = runTest(dispatcher) {
        val smm = data.events.create(EventDraft("SMM 2026")).orFail()
        val navalia = data.events.create(EventDraft("Navalia")).orFail()
        val abc = data.companies.create(CompanyDraft("ABC Marine")).orFail()
        val ana = data.contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Sales Director", companyId = abc.id)).orFail()
        val luis = data.contacts.create(ContactDraft("Luis", "Gil", jobTitle = "Sales Manager")).orFail()
        data.contacts.addToEvent(ana.id, smm.id).orFail()
        data.contacts.addToEvent(luis.id, navalia.id).orFail()

        val vm = detail(smm.id)
        advanceUntilIdle()
        assertEquals("SMM 2026", vm.state.value.event?.name)
        assertEquals(listOf("Ruiz"), vm.state.value.contacts.map { it.contact.lastName })
        assertEquals("ABC Marine", vm.state.value.contacts.single().companyName)

        // «sales» matches both people, but only Ana is in this event.
        vm.onQuery("sales")
        advanceUntilIdle()
        assertEquals(listOf("Ruiz"), vm.state.value.contacts.map { it.contact.lastName })
        assertEquals("sales", vm.state.value.query)
        vm.onQuery("gil")
        advanceUntilIdle()
        assertTrue(vm.state.value.contacts.isEmpty())

        vm.startEdit()
        vm.save(EventDraft(name = ""))
        advanceUntilIdle()
        assertEquals(EditError.NAME_REQUIRED, vm.state.value.editor?.error)
        vm.save(EventDraft(name = "SMM Hamburg 2026", location = "Hamburg", startDate = 5_000))
        advanceUntilIdle()
        assertNull(vm.state.value.editor)
        assertEquals("SMM Hamburg 2026", data.events.get(smm.id)?.name)
        assertEquals(5_000L, data.events.get(smm.id)?.startDate)

        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.state.value.deleted)
        assertNull(data.events.get(smm.id))
        // Its contacts stay.
        assertEquals(2, data.contacts.observeCount().first())
    }

    @Test
    fun `a missing event is reported`() = runTest(dispatcher) {
        val vm = detail("nope")
        advanceUntilIdle()
        assertTrue(vm.state.value.missing)
    }

    @Test
    fun `dates are shown as a range, a single date or nothing`() {
        val base = Event("e", "SMM", createdAt = 0)
        val format: (Long) -> String = { "d$it" }
        assertEquals("d1" to "d2", eventDates(base.copy(startDate = 1, endDate = 2), format))
        assertEquals("d1" to null, eventDates(base.copy(startDate = 1, endDate = 1), format))
        assertEquals("d2" to null, eventDates(base.copy(endDate = 2), format))
        assertNull(eventDates(base, format))
    }
}

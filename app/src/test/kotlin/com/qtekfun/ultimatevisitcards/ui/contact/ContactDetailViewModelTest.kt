package com.qtekfun.ultimatevisitcards.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.contacts.PhoneContactResult
import com.qtekfun.ultimatevisitcards.data.contacts.PhoneContacts
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomTransactionRunner
import com.qtekfun.ultimatevisitcards.domain.export.VCardContact
import com.qtekfun.ultimatevisitcards.domain.merge.MergePlan
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSide
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.usecase.MergeContactsUseCase
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.testing.AppTestData
import com.qtekfun.ultimatevisitcards.ui.merge.MergeUndoHolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var data: AppTestData
    private lateinit var contacts: RoomContactRepository
    private lateinit var companies: RoomCompanyRepository
    private lateinit var sectors: RoomSectorRepository
    private val undoHolder = MergeUndoHolder()

    /** Records what would be written to the phone's contacts. */
    private class FakePhone(var result: PhoneContactResult = PhoneContactResult.Added(1)) : PhoneContacts {
        val added = mutableListOf<VCardContact>()

        override fun add(contact: VCardContact): PhoneContactResult {
            if (result is PhoneContactResult.Added) added += contact
            return result
        }
    }

    private val phone = FakePhone()

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

    private val merge get() = MergeContactsUseCase(contacts, companies, data.cards, RoomTransactionRunner(data.db), data.clock)

    private fun viewModel(id: String) = ContactDetailViewModel(
        SavedStateHandle(mapOf(ContactDetailViewModel.ARG_CONTACT_ID to id)),
        contacts,
        companies,
        sectors,
        data.cards,
        data.events,
        data.store,
        phone,
        merge,
        undoHolder,
        dispatcher
    )

    private suspend fun cardFor(contact: Contact, at: Long, eventId: String? = null): String {
        val id = IdGenerator.Uuid.newId()
        data.store.saveProcessed(id, CardSide.FRONT, AppTestData.cardPhoto())
        data.cards.create(BusinessCardDraft("cards/$id/front_original.jpg", "cards/$id/front.jpg", eventId = eventId, capturedAt = at), id = id).value()
        data.cards.attachToContact(id, contact.id).value()
        return id
    }

    private fun files(cardId: String) = File(folder.root, "files/cards/$cardId")

    @Test
    fun `shows the contact with company, inherited country, events and cards`() = runTest(dispatcher) {
        val sector = sectors.create("Astillero").value()
        val company = companies.create(CompanyDraft("Example Heavy Industries", sector.id, "South Korea")).value()
        val contact = contacts.create(ContactDraft("Ji-ho", "Park", companyId = company.id)).value()
        val smm = data.events.create(EventDraft("SMM 2026")).value()
        val card = cardFor(contact, 1, smm.id)

        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val state = assertIs<ContactDetailUiState.Content>(vm.state.value)
        assertEquals("Astillero", state.sectorName)
        assertEquals("South Korea", state.country)
        assertEquals(listOf("SMM 2026"), state.events.map { it.name })
        assertEquals(listOf(card), state.cards.map { it.card.id })
        assertTrue(state.cards.single().front?.isFile == true)
    }

    @Test
    fun `card history - primary, deleting an old card with its photos, never the only one`() = runTest(dispatcher) {
        val contact = contacts.create(ContactDraft("John", "Smith")).value()
        val old = cardFor(contact, 2026)
        val new = cardFor(contact, 2028)
        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setPrimary(old)
        advanceUntilIdle()
        assertTrue(data.cards.get(old)!!.isPrimary)

        vm.deleteCard(new)
        advanceUntilIdle()
        assertEquals(ContactEvent.CardDeleted, vm.events.value)
        assertFalse(files(new).exists())
        vm.consumed()

        vm.deleteCard(old)
        advanceUntilIdle()
        assertEquals(ContactEvent.OnlyCard, vm.events.value)
        assertTrue(files(old).exists())
    }

    @Test
    fun `deleting the contact removes its cards and photos`() = runTest(dispatcher) {
        val contact = contacts.create(ContactDraft("Ana", "Ruiz")).value()
        val card = cardFor(contact, 1)
        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.deleteContact()
        advanceUntilIdle()
        assertTrue(vm.deleted.value)
        assertEquals(null, data.cards.get(card))
        assertFalse(files(card).exists())
    }

    @Test
    fun `share writes a vcard into the shared cache folder`() = runTest(dispatcher) {
        val company = companies.create(CompanyDraft("ABC Marine", country = "Spain")).value()
        val contact = contacts.create(ContactDraft("José", "Muñoz", companyId = company.id, email = "jose@abc-demo.es")).value()
        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.shareVCard()
        advanceUntilIdle()
        val file = assertIs<ContactEvent.Share>(vm.events.value).file
        assertEquals(File(folder.root, "cache/shared").canonicalPath, file.parentFile!!.canonicalPath)
        val text = file.readText()
        assertTrue(text.contains("FN:José Muñoz") && text.contains("ORG:ABC Marine") && text.contains(";Spain"))
    }

    @Test
    fun `add to phone contacts once, then ask before adding again, and report a missing permission`() = runTest(dispatcher) {
        val contact = contacts.create(ContactDraft("Ana", "Ruiz", mobile = "+34 600 111 222")).value()
        val vm = viewModel(contact.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.addToPhone()
        advanceUntilIdle()
        assertEquals(ContactEvent.AddedToPhone, vm.events.value)
        assertEquals("+34 600 111 222", phone.added.single().mobile)
        assertNotNull(contacts.get(contact.id)!!.exportedToPhoneAt)
        vm.consumed()
        advanceUntilIdle()

        vm.addToPhone()
        assertIs<ContactEvent.AlreadyAddedToPhone>(vm.events.value)
        vm.consumed()
        vm.addToPhone(again = true)
        advanceUntilIdle()
        assertEquals(2, phone.added.size)
        vm.consumed()

        phone.result = PhoneContactResult.Failed(PhoneContactResult.Reason.PERMISSION)
        vm.addToPhone(again = true)
        advanceUntilIdle()
        assertEquals(ContactEvent.PhonePermissionMissing, vm.events.value)
    }

    @Test
    fun `a merge into this contact can be undone from the sheet`() = runTest(dispatcher) {
        val a = contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Manager")).value()
        val b = contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Director")).value()
        val vm = viewModel(a.id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val undo = merge(a.id, b.id, MergePlan.of(MergeSide.of(a, null, 1), MergeSide.of(b, null, 2))).value()
        undoHolder.offer(undo)
        advanceUntilIdle()
        assertEquals(undo, vm.undo.value)
        vm.undoMerge()
        advanceUntilIdle()
        assertEquals(ContactEvent.MergeUndone, vm.events.value)
        assertEquals(2, contacts.observeAll().first().size)
    }
}

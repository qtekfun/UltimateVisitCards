package com.qtekfun.ultimatevisitcards.ui.review

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomCompanyRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomContactRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomEventRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomSectorRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomTransactionRunner
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.settings.UiPreferences
import com.qtekfun.ultimatevisitcards.domain.dedupe.DuplicateDetector
import com.qtekfun.ultimatevisitcards.domain.dedupe.MatchReason
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedValue
import com.qtekfun.ultimatevisitcards.domain.merge.MergeField
import com.qtekfun.ultimatevisitcards.domain.merge.MergeSource
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.text.CompanyNameNormalizer
import com.qtekfun.ultimatevisitcards.domain.usecase.AddCardToContactUseCase
import com.qtekfun.ultimatevisitcards.domain.usecase.SaveReviewedCardUseCase
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReviewViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var cards: RoomBusinessCardRepository
    private lateinit var companies: RoomCompanyRepository
    private lateinit var contacts: RoomContactRepository
    private lateinit var events: RoomEventRepository
    private lateinit var sectors: RoomSectorRepository
    private lateinit var store: ImageStore
    private val cardId = "55555555-5555-4555-8555-555555555555"
    private val preferences by lazy { UiPreferences(ApplicationProvider.getApplicationContext()) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(dispatcher)
            .build()
        val indexer = SearchIndexer(db)
        val clock = Clock { 1L }
        val ids = IdGenerator.Uuid
        cards = RoomBusinessCardRepository(db, indexer, clock, ids)
        companies = RoomCompanyRepository(db, indexer, CompanyNameNormalizer.fromBundledDictionary(), clock, ids)
        contacts = RoomContactRepository(db, indexer, clock, ids)
        events = RoomEventRepository(db, indexer, clock, ids)
        sectors = RoomSectorRepository(db, indexer, clock, ids)
        store = ImageStore(folder.newFolder("files"), folder.newFolder("cache"), dispatcher)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(id: String = cardId, fromQueue: Boolean = false, target: String? = null) = ReviewViewModel(
        SavedStateHandle(
            listOfNotNull(
                ReviewViewModel.ARG_CARD_ID to id,
                ReviewViewModel.ARG_FROM_QUEUE to fromQueue,
                target?.let {
                    ReviewViewModel.ARG_TARGET_CONTACT to
                        it
                }
            ).toMap()
        ),
        cards,
        companies,
        contacts,
        events,
        sectors,
        store,
        DuplicateDetector(),
        SaveReviewedCardUseCase(companies, contacts, cards, RoomTransactionRunner(db)),
        AddCardToContactUseCase(contacts, companies, cards, RoomTransactionRunner(db), Clock { 1L }),
        preferences
    )

    private suspend fun card(status: OcrStatus = OcrStatus.PENDING, fields: ExtractedFields? = null) {
        cards.create(BusinessCardDraft("cards/$cardId/front_original.jpg", "cards/$cardId/front.jpg", capturedAt = 1), id = cardId)
        if (status != OcrStatus.PENDING) cards.updateOcr(cardId, status, rawOcrText = "text", extractedJson = fields?.toJson())
    }

    private fun proposal(vararg values: Pair<CardField, Pair<String, Float>>) =
        ExtractedFields(values.associate { (field, v) -> field to ExtractedValue(v.first, v.second, v.first) })

    private val ana = proposal(
        CardField.FIRST_NAME to ("Ana" to 0.9f),
        CardField.LAST_NAME to ("Ruiz" to 0.9f),
        CardField.COMPANY to ("Navaltech S.L." to 0.8f),
        CardField.EMAIL to ("ana@navaltech-demo.es" to 0.95f),
        CardField.JOB_TITLE to ("Directora" to 0.4f)
    )

    @Test
    fun `while the OCR reads, the form is editable and is completed without overwriting what was typed`() = runTest(dispatcher) {
        card(OcrStatus.PENDING)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.reading)
        vm.onChange(CardField.FIRST_NAME, "Anabel")

        cards.updateOcr(cardId, OcrStatus.PENDING_REVIEW, extractedJson = ana.toJson())
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.reading)
        assertEquals("Anabel", state.value(CardField.FIRST_NAME))
        assertEquals("Ruiz", state.value(CardField.LAST_NAME))
        // Low confidence → highlighted.
        assertEquals(setOf(CardField.JOB_TITLE), state.doubtful)
        vm.onChange(CardField.JOB_TITLE, "Directora Comercial")
        assertTrue(vm.state.value.doubtful.isEmpty())
    }

    @Test
    fun `the doubtful fields follow the confidence threshold of Settings`() = runTest(dispatcher) {
        preferences.setConfidenceThreshold(0.3f)
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel()
        advanceUntilIdle()
        // 0.4 is above a 0.3 threshold: nothing is highlighted.
        assertTrue(vm.state.value.doubtful.isEmpty())
    }

    @Test
    fun `a failed OCR leaves an empty form with a clear message`() = runTest(dispatcher) {
        card(OcrStatus.OCR_ERROR)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.ocrFailed)
        assertTrue(vm.state.value.values.isEmpty())
    }

    @Test
    fun `a missing card is reported`() = runTest(dispatcher) {
        val vm = viewModel("66666666-6666-4666-8666-666666666666")
        advanceUntilIdle()
        assertTrue(vm.state.value.missing)
    }

    @Test
    fun `existing companies are suggested and bring their sector`() = runTest(dispatcher) {
        val sector = sectors.create("Astillero").value()
        companies.create(CompanyDraft("Navantia Ficticia S.A.", sectorId = sector.id))
        card(OcrStatus.PENDING_REVIEW, ExtractedFields())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onChange(CardField.COMPANY, "navan")
        val suggestion = vm.state.value.companySuggestions.single()
        vm.chooseCompany(suggestion)
        assertEquals("Navantia Ficticia S.A.", vm.state.value.value(CardField.COMPANY))
        assertEquals(sector.id, vm.state.value.sectorId)
    }

    @Test
    fun `countries are suggested in any of the five languages`() = runTest(dispatcher) {
        card(OcrStatus.PENDING_REVIEW, ExtractedFields())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onChange(CardField.COUNTRY, "Corea")
        assertTrue("South Korea" in vm.state.value.countrySuggestions)
        vm.chooseCountry("South Korea")
        assertEquals("South Korea", vm.state.value.value(CardField.COUNTRY))
    }

    @Test
    fun `events and sectors can be created without leaving the review`() = runTest(dispatcher) {
        val existing = sectors.create("Logística").value()
        card(OcrStatus.PENDING_REVIEW, ExtractedFields())
        val vm = viewModel()
        advanceUntilIdle()
        vm.createEvent("SMM 2026")
        advanceUntilIdle()
        assertEquals("SMM 2026", events.get(assertNotNull(vm.state.value.eventId))!!.name)
        // A sector that already exists is selected instead of duplicated.
        vm.createSector("LOGISTICA")
        advanceUntilIdle()
        assertEquals(existing.id, vm.state.value.sectorId)
    }

    @Test
    fun `saving creates the contact and leaves`() = runTest(dispatcher) {
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel()
        advanceUntilIdle()
        vm.saveAndNext()
        advanceUntilIdle()
        assertEquals(ReviewExit.ScanNext, vm.state.value.exit)
        val saved = contacts.observeAll().first().single()
        assertEquals("ana@navaltech-demo.es", saved.email)
        assertEquals(OcrStatus.REVIEWED, cards.get(cardId)!!.ocrStatus)
    }

    @Test
    fun `an empty form is not saved`() = runTest(dispatcher) {
        card(OcrStatus.OCR_ERROR)
        val vm = viewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(ReviewError.EMPTY, vm.state.value.error)
        assertEquals(null, vm.state.value.exit)
    }

    @Test
    fun `a possible duplicate stops the save until the user decides`() = runTest(dispatcher) {
        contacts.create(ContactDraft("Ana", "Ruiz Pérez", email = "ana@navaltech-demo.es"))
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        val candidate = vm.state.value.duplicates.single()
        assertEquals(MatchReason.EMAIL, candidate.reason)
        assertEquals(1, contacts.observeAll().first().size)

        vm.viewExisting(candidate)
        assertEquals("Ruiz Pérez", vm.state.value.preview?.contact?.lastName)
        vm.closePreview()

        vm.saveAsNew()
        advanceUntilIdle()
        assertEquals(ReviewExit.Done, vm.state.value.exit)
        assertEquals(2, contacts.observeAll().first().size)
    }

    @Test
    fun `from the queue, saving moves on to the next pending card and ends when none is left`() = runTest(dispatcher) {
        card(OcrStatus.PENDING_REVIEW, ana)
        val second = "77777777-7777-4777-8777-777777777777"
        cards.create(BusinessCardDraft("cards/$second/front_original.jpg", "cards/$second/front.jpg", capturedAt = 2), id = second)
        cards.updateOcr(second, OcrStatus.PENDING_REVIEW, extractedJson = proposal(CardField.FIRST_NAME to ("Luis" to 0.9f)).toJson())

        val first = viewModel(fromQueue = true)
        advanceUntilIdle()
        assertEquals(SaveAndNext.REVIEW, first.next)
        first.saveAndNext()
        advanceUntilIdle()
        assertEquals(ReviewExit.ReviewNext(second), first.state.value.exit)

        val last = viewModel(second, fromQueue = true)
        advanceUntilIdle()
        last.saveAndNext()
        advanceUntilIdle()
        assertEquals(ReviewExit.Done, last.state.value.exit)
    }

    @Test
    fun `merge from the warning - the card joins the existing contact with the chosen values`() = runTest(dispatcher) {
        val existing = contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Manager", email = "ana@navaltech-demo.es")).value()
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        vm.mergeInto(vm.state.value.duplicates.single())
        advanceUntilIdle()
        val merge = assertNotNull(vm.state.value.merge)
        // The card is newer: its job title wins by default; keep the old one instead.
        assertEquals("Directora", merge.plan.chosen(MergeField.JOB_TITLE))
        vm.chooseMerge(MergeField.JOB_TITLE, MergeSource.EXISTING)
        vm.confirmMerge()
        advanceUntilIdle()

        assertEquals(ReviewExit.Done, vm.state.value.exit)
        assertEquals(listOf(existing.id), contacts.observeAll().first().map { it.id })
        val updated = contacts.get(existing.id)!!
        assertEquals("Manager", updated.jobTitle)
        assertEquals("Navaltech S.L.", companies.get(updated.companyId!!)!!.name)
        assertEquals(existing.id, cards.get(cardId)!!.contactId)
        assertEquals(OcrStatus.REVIEWED, cards.get(cardId)!!.ocrStatus)
    }

    @Test
    fun `add as new card keeps the existing data and only fills the gaps`() = runTest(dispatcher) {
        val existing = contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Manager", email = "ana@navaltech-demo.es")).value()
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        vm.addAsNewCard(vm.state.value.duplicates.single())
        advanceUntilIdle()
        val updated = contacts.get(existing.id)!!
        assertEquals("Manager", updated.jobTitle)
        assertNotNull(updated.companyId)
        assertEquals(existing.id, cards.get(cardId)!!.contactId)
    }

    @Test
    fun `a card taken for a contact is compared with that contact on save`() = runTest(dispatcher) {
        val target = contacts.create(ContactDraft("Ana", "Ruiz", jobTitle = "Manager")).value()
        card(OcrStatus.PENDING_REVIEW, ana)
        val vm = viewModel(target = target.id)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals(target.id, vm.state.value.merge?.contact?.id)
        vm.confirmMerge()
        advanceUntilIdle()
        assertEquals(target.id, cards.get(cardId)!!.contactId)
        assertEquals(1, contacts.observeAll().first().size)
    }

    private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value
}

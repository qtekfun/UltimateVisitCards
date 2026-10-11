package com.qtekfun.ultimatevisitcards

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyRepository
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactRepository
import com.qtekfun.ultimatevisitcards.domain.repository.SectorRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The production database as Hilt builds it (Android SQLite driver, seeding callback, FTS4 table) works
 * end to end: the first launch seeds the sectors and a saved contact is found by search.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DataWiringTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var sectors: SectorRepository

    @Inject
    lateinit var companies: CompanyRepository

    @Inject
    lateinit var contacts: ContactRepository

    @Before
    fun inject() {
        hilt.inject()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun seedsSectorsAndSearchesWithTheAndroidDriver() = runBlocking {
        val seeded = sectors.observeAll().first()
        assertEquals(14, seeded.size)

        val company = companies.create(CompanyDraft("Ejemplo Naval S.L.", sectorId = seeded.first().id)) as DomainResult.Success
        contacts.create(ContactDraft("José", "Muñoz", companyId = company.value.id))

        val found = contacts.search("munoz shipyard").first()
        assertEquals(listOf("Muñoz"), found.map { it.lastName })
        assertTrue(contacts.search("nadie").first().isEmpty())
    }
}

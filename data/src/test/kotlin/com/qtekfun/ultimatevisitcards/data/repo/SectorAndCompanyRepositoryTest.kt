package com.qtekfun.ultimatevisitcards.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.model.DomainError
import com.qtekfun.ultimatevisitcards.domain.model.DomainResult
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SectorAndCompanyRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val data = TestData(dispatcher)

    @After
    fun close() = data.close()

    @Test
    fun `sectors keep creation order and reject blank names`() = runTest(dispatcher) {
        data.sectors.create("Astillero").value()
        data.sectors.create(" Armador ").value()
        assertEquals(listOf("Astillero", "Armador"), data.sectors.observeAll().first().map { it.name })
        assertEquals(listOf(0, 1), data.sectors.observeAll().first().map { it.sortOrder })
        assertEquals(DomainResult.Failure(DomainError.Invalid(DomainError.Field.NAME)), data.sectors.create("  "))
    }

    @Test
    fun `sector names are unique ignoring case and accents`() = runTest(dispatcher) {
        val electronics = data.sectors.create("Electrónica").value()
        assertEquals(
            DomainResult.Failure(DomainError.Conflict(DomainError.Field.NAME, electronics.id)),
            data.sectors.create("ELECTRONICA")
        )
        val other = data.sectors.create("Otro").value()
        assertEquals(
            DomainResult.Failure(DomainError.Conflict(DomainError.Field.NAME, electronics.id)),
            data.sectors.rename(other.id, "electrónica")
        )
        // Renaming to itself with another spelling is fine.
        assertEquals("ELECTRÓNICA", data.sectors.rename(electronics.id, "ELECTRÓNICA").value().name)
    }

    @Test
    fun `missing sectors report NotFound`() = runTest(dispatcher) {
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.sectors.delete("nope"))
        assertEquals(DomainResult.Failure(DomainError.NotFound("nope")), data.sectors.rename("nope", "X"))
    }

    @Test
    fun `companies are unique by normalised name and found by any spelling`() = runTest(dispatcher) {
        val abc = data.companies.create(CompanyDraft("ABC Marine GmbH", country = " Germany ")).value()
        assertEquals("Germany", abc.country)
        assertEquals(
            DomainResult.Failure(DomainError.Conflict(DomainError.Field.NAME, abc.id)),
            data.companies.create(CompanyDraft("abc marine"))
        )
        assertEquals(abc.id, data.companies.findByName("ABC MARINE Ltd.")?.id)
        assertNull(data.companies.findByName("XYZ Marine"))
        assertNull(data.companies.findByName(""))
    }

    @Test
    fun `updating a company keeps its id, revalidates the name and bumps updatedAt`() = runTest(dispatcher) {
        val abc = data.companies.create(CompanyDraft("ABC Marine")).value()
        val other = data.companies.create(CompanyDraft("Other Marine")).value()
        assertEquals(
            DomainResult.Failure(DomainError.Conflict(DomainError.Field.NAME, abc.id)),
            data.companies.update(other.copy(name = "ABC Marine S.L."))
        )
        val renamed = data.companies.update(abc.copy(name = "ABC Marine Services", website = "")).value()
        assertEquals(abc.id, renamed.id)
        assertNull(renamed.website)
        assert(renamed.updatedAt > abc.updatedAt)
        assertEquals(listOf("ABC Marine Services", "Other Marine"), data.companies.observeAll().first().map { it.name })
    }
}

package com.qtekfun.ultimatevisitcards.domain.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class DomainResultTest {
    @Test
    fun `map transforms only successes`() {
        assertEquals(DomainResult.Success(4), DomainResult.Success(2).map { it * 2 })
        val failure: DomainResult<Int> = DomainResult.Failure(DomainError.NotFound("x"))
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `getOrNull`() {
        assertEquals(1, DomainResult.Success(1).getOrNull())
        assertNull(DomainResult.Failure(DomainError.NotFound("x")).getOrNull())
    }
}

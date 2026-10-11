package com.qtekfun.ultimatevisitcards.domain.search

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `every term is a prefix and all must match`() {
        assertEquals("hyundai* ship*", SearchQuery.toFtsMatch("Hyundai Ship"))
    }

    @Test
    fun `terms are folded like the index`() {
        assertEquals("munoz* corea*", SearchQuery.toFtsMatch("Muñoz CORÉA"))
    }

    @Test
    fun `FTS syntax cannot be injected`() {
        assertEquals("a* b* c*", SearchQuery.toFtsMatch("\"a\" -b* (c)"))
        assertEquals("name* x*", SearchQuery.toFtsMatch("name:x"))
    }

    @Test
    fun `repeated terms are kept once`() {
        assertEquals("smm*", SearchQuery.toFtsMatch("SMM smm"))
    }

    @Test
    fun `nothing searchable gives null`() {
        assertNull(SearchQuery.toFtsMatch("   "))
        assertNull(SearchQuery.toFtsMatch("*\"()"))
    }
}

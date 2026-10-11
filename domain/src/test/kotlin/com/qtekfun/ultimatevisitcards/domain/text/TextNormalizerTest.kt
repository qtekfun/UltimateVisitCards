package com.qtekfun.ultimatevisitcards.domain.text

import kotlin.test.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `folds case and diacritics in the five card languages`() {
        assertEquals("jose munoz", TextNormalizer.fold("José Muñoz"))
        assertEquals("francois lefevre", TextNormalizer.fold("François LEFÈVRE"))
        assertEquals("jurgen strasse", TextNormalizer.fold("Jürgen Straße"))
        assertEquals("niccolo citta", TextNormalizer.fold("Niccolò Città"))
    }

    @Test
    fun `folds letters that have no decomposition`() {
        assertEquals("soren aero lodz", TextNormalizer.fold("Søren Æro Łódź"))
    }

    @Test
    fun `collapses whitespace`() {
        assertEquals("ship management", TextNormalizer.fold("  Ship \t Management\n"))
    }

    @Test
    fun `tokens split on punctuation and symbols`() {
        assertEquals(listOf("john", "abc", "marine", "com"), TextNormalizer.tokens("John@ABC-Marine.com"))
        assertEquals(emptyList(), TextNormalizer.tokens(" .,;-* "))
    }
}

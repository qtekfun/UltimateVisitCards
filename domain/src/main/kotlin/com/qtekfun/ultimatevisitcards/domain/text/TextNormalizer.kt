package com.qtekfun.ultimatevisitcards.domain.text

import java.text.Normalizer

/**
 * Folds text for comparison and search: lower case, no diacritics, single spaces.
 *
 * The app does this itself instead of relying on the FTS tokenizer's `remove_diacritics=2`, which needs
 * SQLite 3.27 while Android 10 (minSdk 29) ships 3.22 (see docs/decisions.md).
 */
object TextNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    // Letters that NFD does not decompose into a base letter plus a mark.
    private val specialLetters = mapOf(
        'ß' to "ss",
        'æ' to "ae",
        'œ' to "oe",
        'ø' to "o",
        'ł' to "l",
        'đ' to "d",
        'ð' to "d",
        'þ' to "th",
        'ı' to "i"
    )

    fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")
        val replaced = buildString(decomposed.length) {
            decomposed.forEach { c -> append(specialLetters[c] ?: c) }
        }
        return replaced.replace(whitespace, " ").trim()
    }

    /** Folded text split into letter/digit tokens (punctuation and symbols are separators). */
    fun tokens(text: String): List<String> = fold(text).split(nonAlphanumeric).filter { it.isNotEmpty() }

    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
}

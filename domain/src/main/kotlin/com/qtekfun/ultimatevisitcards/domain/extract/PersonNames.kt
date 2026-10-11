package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer

internal data class SplitName(val first: String, val last: String, val prefix: String?)

/**
 * Splits a personal name into first name and surname(s) with per-language rules: particles (`de`, `van`,
 * `von`, `di`…) start the surname, Spanish names carry two surnames, a surname written in capitals
 * (`Jean DUPONT`) is the surname, `SURNAME, Name` is honoured. Titles (`Dr.`, `Dipl.-Ing.`) are removed and
 * returned apart.
 */
internal class PersonNames(private val dict: ExtractionDictionaries) {
    fun stripPrefixes(words: List<String>): Pair<List<String>, String?> {
        val prefixes = words.takeWhile { ExtractionDictionaries.compact(it) in dict.namePrefixes }
        return words.drop(prefixes.size) to prefixes.joinToString(" ").takeIf { it.isNotEmpty() }
    }

    fun split(name: String, language: String?): SplitName {
        if (name.contains(',')) {
            val last = name.substringBefore(',').trim()
            val (first, prefix) = stripPrefixes(name.substringAfter(',').trim().split(' ').filter { it.isNotEmpty() })
            if (first.isNotEmpty() && last.isNotEmpty()) return SplitName(first.joinToString(" "), last, prefix)
        }
        val (words, prefix) = stripPrefixes(name.split(' ').filter { it.isNotEmpty() })
        if (words.size <= 1) return SplitName(words.joinToString(" "), "", prefix)
        val folded = words.map { TextNormalizer.fold(it) }

        // A particle starts the surname: «Ludwig van Beethoven», «Juan de la Cruz».
        val particleAt = (1 until words.size).firstOrNull { i -> dict.nameParticles.any { p -> folded.drop(i).take(p.size) == p && i + p.size < words.size } }
        if (particleAt != null) return SplitName(words.take(particleAt).joinToString(" "), words.drop(particleAt).joinToString(" "), prefix)

        // A surname in capitals among mixed-case words: «Jean DUPONT», «DUPONT Jean».
        val caps = words.map { w -> w.length > 1 && w.any { it.isLetter() } && w.filter { it.isLetter() }.all { it.isUpperCase() } }
        if (caps.any { it } && caps.any { !it }) {
            return SplitName(words.filterIndexed { i, _ -> !caps[i] }.joinToString(" "), words.filterIndexed { i, _ -> caps[i] }.joinToString(" "), prefix)
        }

        // Spanish-speaking: two surnames; a known second given name keeps «María José» together.
        if (language == "es" && words.size >= 3) {
            val firstCount = if (words.size == 3 && folded[1] in dict.compoundFirstNames) 2 else words.size - 2
            return SplitName(words.take(firstCount).joinToString(" "), words.drop(firstCount).joinToString(" "), prefix)
        }
        return SplitName(words.dropLast(1).joinToString(" "), words.last(), prefix)
    }
}

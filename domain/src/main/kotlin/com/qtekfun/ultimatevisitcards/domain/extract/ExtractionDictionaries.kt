package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer

/** A country with its main language and every way a card may write it. */
data class CountryEntry(
    /** Normalised English name, e.g. `South Korea`. */
    val name: String,
    /** ISO 639-1 language, used to split personal names (`es` → two surnames). */
    val language: String,
    /** Folded token sequences of the written names. */
    val aliases: List<List<String>>,
    /** ISO-2/ISO-3 codes (upper case); only trusted when they stand alone. */
    val codes: Set<String>
)

/** Mobile numbering of a country: [trunk] prefix used inside the country, mobile [prefixes], national [lengths]. */
data class MobileRule(val trunk: String, val prefixes: List<String>, val lengths: Set<Int>)

enum class LabelKind { PHONE, MOBILE, FAX, EMAIL, WEB }

/**
 * The data the extractor relies on, loaded from `dictionaries/` text files (never embedded in code, see
 * CONTRIBUTING.md). Everything is folded with [TextNormalizer] on load.
 */
class ExtractionDictionaries(
    val strongTitles: List<List<String>>,
    val weakTitles: List<List<String>>,
    val titleSuffixes: List<String>,
    val countries: List<CountryEntry>,
    /** International prefix (digits) → country name. */
    val phonePrefixes: Map<String, String>,
    val mobileRules: Map<String, MobileRule>,
    val labels: Map<LabelKind, Set<String>>,
    val streetWords: Set<String>,
    val publicEmailDomains: Set<String>,
    val nameParticles: List<List<String>>,
    val namePrefixes: Set<String>,
    val compoundFirstNames: Set<String>,
    /** Letter prefix of a postal code (`D` in `D-20457`) → country name. */
    val postalLetterPrefixes: Map<String, String>,
    val legalSuffixes: List<List<String>>,
    /** Postal code formats, as whole-word regular expressions. */
    val postalCodes: List<Regex>,
    /** Words that give away the card's language (ISO 639-1) when no country is known. */
    val languageMarkers: Map<String, Set<String>>
) {
    fun country(name: String): CountryEntry? = countries.firstOrNull { it.name == name }

    companion object {
        private const val DIR = "/dictionaries/"

        /** The dictionaries bundled with the app. */
        val bundled: ExtractionDictionaries by lazy { load() }

        private fun lines(file: String): List<String> {
            val stream = requireNotNull(ExtractionDictionaries::class.java.getResourceAsStream(DIR + file)) { "Missing dictionary $file" }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
        }

        private fun tokens(text: String) = TextNormalizer.tokens(text)

        private fun load(): ExtractionDictionaries {
            val titles = lines("job_titles.txt")
            val countries = lines("countries.txt").map { line ->
                val (name, language, aliases) = line.split('|')
                val all = aliases.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                CountryEntry(
                    name = name,
                    language = language,
                    aliases = (all.filterNot { isCode(it) } + name).map { tokens(it) }.filter { it.isNotEmpty() }.distinct(),
                    codes = all.filter { isCode(it) }.map { it.replace(".", "") }.toSet()
                )
            }
            val labels = lines("labels.txt").associate { line ->
                val kind = LabelKind.valueOf(line.substringBefore(':').trim().uppercase())
                kind to line.substringAfter(':').split(',').map { TextNormalizer.fold(it).trim() }.filter { it.isNotEmpty() }.toSet()
            }
            return ExtractionDictionaries(
                strongTitles = titles.filter { it[0] != '~' && it[0] != '*' }.map { tokens(it) },
                weakTitles = titles.filter { it[0] == '~' }.map { tokens(it.drop(1)) },
                titleSuffixes = titles.filter { it[0] == '*' }.map { TextNormalizer.fold(it.drop(1)) },
                countries = countries,
                phonePrefixes = lines("phone_prefixes.txt").associate { it.substringBefore('|') to it.substringAfter('|') },
                mobileRules = lines("mobile_prefixes.txt").associate { line ->
                    val parts = line.split('|')
                    parts[0] to MobileRule(
                        trunk = parts[1],
                        prefixes = parts[2].split(',').map { it.trim() },
                        lengths = parts[3].split(',').map { it.trim().toInt() }.toSet()
                    )
                },
                labels = labels,
                streetWords = lines("street_words.txt").map { TextNormalizer.fold(it).trim('.') }.toSet(),
                publicEmailDomains = lines("public_email_domains.txt").map { it.lowercase() }.toSet(),
                nameParticles = lines("name_particles.txt").map { tokens(it) }.sortedByDescending { it.size },
                namePrefixes = lines("name_prefixes.txt").map { compact(it) }.toSet(),
                compoundFirstNames = lines("compound_first_names.txt").map { TextNormalizer.fold(it) }.toSet(),
                postalLetterPrefixes = lines("postal_letter_prefixes.txt").associate { it.substringBefore('|') to it.substringAfter('|') },
                legalSuffixes = lines("legal_suffixes.txt").map { tokens(it) }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.size },
                postalCodes = lines("postal_patterns.txt").map { Regex("(?<![\\p{L}\\d+])(?:$it)(?![\\p{L}\\d])") },
                languageMarkers = lines("language_markers.txt").associate { line ->
                    line.substringBefore(':').trim() to
                        line.substringAfter(':').split(',').map { TextNormalizer.fold(it).trim() }.filter { it.isNotEmpty() }.toSet()
                }
            )
        }

        /** ISO codes are written in capitals: `ES`, `KOR`, `U.S.A.`. */
        private fun isCode(alias: String): Boolean {
            val letters = alias.replace(".", "")
            return letters.length in 2..3 && letters.all { it in 'A'..'Z' }
        }

        /** Folded, without dots and hyphens: `Dipl.-Ing.` → `dipling`, the same for the file and the card. */
        internal fun compact(text: String): String = TextNormalizer.fold(text).filter { it.isLetterOrDigit() }
    }
}

package com.qtekfun.ultimatevisitcards.domain.text

/**
 * Key that identifies a company regardless of spelling details: folded, without punctuation and without
 * trailing legal forms (`ABC Marine GmbH` and `abc marine` are the same company).
 *
 * The legal forms come from the `dictionaries/legal_suffixes.txt` data file, not from code.
 */
class CompanyNameNormalizer(legalSuffixes: List<String>) {
    // Longest first, so `gmbh co kg` is removed before `kg`.
    private val suffixes: List<List<String>> = legalSuffixes
        .map { TextNormalizer.tokens(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.size }

    fun normalize(name: String): String {
        var tokens = TextNormalizer.tokens(name)
        var stripped = true
        while (stripped) {
            stripped = false
            for (suffix in suffixes) {
                // Never strip the whole name: a company called just «AG» keeps it.
                if (tokens.size > suffix.size && tokens.takeLast(suffix.size) == suffix) {
                    tokens = tokens.dropLast(suffix.size)
                    stripped = true
                    break
                }
            }
        }
        return tokens.joinToString(" ")
    }

    companion object {
        private const val RESOURCE = "/dictionaries/legal_suffixes.txt"

        /** Loads the bundled dictionary (one legal form per line, `#` starts a comment). */
        fun fromBundledDictionary(): CompanyNameNormalizer {
            val stream = requireNotNull(CompanyNameNormalizer::class.java.getResourceAsStream(RESOURCE)) {
                "Missing dictionary $RESOURCE"
            }
            val lines = stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
            return CompanyNameNormalizer(lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() })
        }
    }
}

package com.qtekfun.ultimatevisitcards.domain.search

import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer

/** Turns what the user typed into an FTS4 MATCH expression. */
object SearchQuery {
    /**
     * Each term becomes a prefix query (`term*`) and all of them must match (implicit AND), e.g.
     * `Hyundai Ship` → `hyundai* ship*`. Returns null when nothing searchable is left.
     *
     * Terms are folded like the index ([TextNormalizer]) and FTS operators can never be injected: only
     * letters and digits survive tokenisation.
     */
    fun toFtsMatch(input: String): String? = toFtsMatch(input, emptyMap())

    /**
     * Free text plus per-column filters, all ANDed: `{"jobTitle": "sales dir"}` adds `jobTitle:sales*
     * jobTitle:dir*`. Column names are those of the index; values are tokenised like the free text.
     */
    fun toFtsMatch(input: String, columns: Map<String, String>): String? {
        val terms = TextNormalizer.tokens(input).distinct().map { "$it*" } +
            columns.flatMap { (column, value) -> TextNormalizer.tokens(value).distinct().map { "$column:$it*" } }
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }
}

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
    fun toFtsMatch(input: String): String? = TextNormalizer.tokens(input)
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ") { "$it*" }
}

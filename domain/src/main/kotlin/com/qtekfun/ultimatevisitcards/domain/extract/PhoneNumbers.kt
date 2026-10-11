package com.qtekfun.ultimatevisitcards.domain.extract

/** A phone number found on a line, already classified. */
internal data class FoundPhone(val raw: String, val normalized: String, val kind: LabelKind?, val country: String?)

/**
 * Phone numbers: sequences with `+`, brackets, spaces, hyphens, dots and slashes holding 7-15 digits.
 * Labels in any of the five languages decide phone / mobile / fax; without a label, a short table of mobile
 * prefixes does (docs/04-ocr-extraccion.md).
 */
internal class PhoneNumbers(private val dict: ExtractionDictionaries) {
    private val candidate = Regex("(?<![\\p{L}\\d])(?:\\+|00)?\\s?\\(?\\d[\\d\\s().\\-/]{5,}\\d(?![\\p{L}\\d])")
    private val labelKinds = listOf(LabelKind.FAX, LabelKind.MOBILE, LabelKind.PHONE)

    /** Every number in [text] with the label written before it (if any). */
    fun find(text: String): List<FoundPhone> {
        val found = mutableListOf<FoundPhone>()
        var previousEnd = 0
        for (match in candidate.findAll(text)) {
            val digits = match.value.count { it.isDigit() }
            if (digits !in 7..15) continue
            val before = text.substring(previousEnd, match.range.first)
            previousEnd = match.range.last + 1
            val kind = labelOf(before)
            val normalized = normalize(match.value)
            found += FoundPhone(match.value.trim(), normalized, kind, countryOf(normalized))
        }
        return found
    }

    /** The last label word before the number: «Tel.: … Mobile:» → MOBILE. */
    private fun labelOf(before: String): LabelKind? {
        val words = com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer.fold(before)
            .split(Regex("[^\\p{L}/-]+"))
            .filter { it.isNotEmpty() }
        for (word in words.asReversed()) {
            labelKinds.firstOrNull { word in dict.labels[it].orEmpty() }?.let { return it }
        }
        return null
    }

    /** International format when the card gives a country prefix; otherwise as printed. */
    fun normalize(raw: String): String {
        var s = raw.trim().replace("(0)", " ").replace(Regex("\\s+"), " ").trim()
        if (s.startsWith("00")) s = "+" + s.removePrefix("00").trimStart()
        if (s.startsWith("+ ")) s = "+" + s.removePrefix("+ ")
        return s.trim().trimEnd('.', '-', '/')
    }

    /** Country from the international prefix (longest match); `+1` is ambiguous and ignored. */
    fun countryOf(normalized: String): String? {
        if (!normalized.startsWith("+")) return null
        val digits = normalized.filter { it.isDigit() }
        return (3 downTo 1).firstNotNullOfOrNull { n -> dict.phonePrefixes[digits.take(n)] }
    }

    /**
     * Mobile by prefix: for an international number, the rules of its country; for a national one, of
     * [cardCountry] if known, otherwise of the five reference countries of docs/04 (ES, DE, FR, IT, UK).
     */
    fun isMobile(phone: FoundPhone, cardCountry: String?): Boolean {
        val digits = phone.normalized.filter { it.isDigit() }
        if (phone.normalized.startsWith("+")) {
            val country = phone.country ?: return false
            val rule = dict.mobileRules[country] ?: return false
            val prefix = dict.phonePrefixes.entries.filter { it.value == country }.map { it.key }.firstOrNull { digits.startsWith(it) } ?: return false
            return matches(rule.trunk + digits.removePrefix(prefix), rule)
        }
        val rules = cardCountry?.let { listOfNotNull(dict.mobileRules[it]) } ?: DEFAULT_COUNTRIES.mapNotNull { dict.mobileRules[it] }
        return rules.any { matches(digits, it) }
    }

    private fun matches(national: String, rule: MobileRule) = national.length in rule.lengths && rule.prefixes.any { national.startsWith(it) }

    private companion object {
        val DEFAULT_COUNTRIES = listOf("Spain", "Germany", "France", "Italy", "United Kingdom")
    }
}

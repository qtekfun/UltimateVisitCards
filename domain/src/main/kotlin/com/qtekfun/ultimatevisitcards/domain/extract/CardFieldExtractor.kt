package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Proposes the fields of a business card from its OCR text (docs/04-ocr-extraccion.md §3). Deterministic:
 * patterns, dictionaries and a few layout signals (line height and order), never the engine.
 *
 * Order: email → website → country named on the card → phones → address → country hints → job title →
 * company → name. Each detector takes what it recognises out of the lines, so later ones only see what is
 * left. Never invents: a field without enough evidence stays empty.
 */
class CardFieldExtractor @Inject constructor() {
    private val dict = ExtractionDictionaries.bundled
    private val phones = PhoneNumbers(dict)
    private val names = PersonNames(dict)
    private val allLabels = dict.labels.values.flatten().toSet()

    fun extract(front: OcrResult, back: OcrResult? = null): ExtractedFields = Run(CardLines.collect(front, back)).extract()

    private inner class Run(private val lines: List<CardLine>) {
        private val fields = mutableMapOf<CardField, ExtractedValue>()
        private val medianHeight = lines.map { it.height }.sorted().let { if (it.isEmpty()) 1f else max(1f, it[it.size / 2]) }
        private val extraLines = mutableListOf<String>()
        private var titleLine: CardLine? = null
        private var countryLanguage: String? = null

        fun extract(): ExtractedFields {
            detectEmail()
            detectWebsite()
            detectNamedCountry()
            detectPhones()
            detectAddress()
            detectCountryHints()
            detectJobTitle()
            detectCompany()
            detectName()
            val unassigned = extraLines + lines.filter { !it.used && it.remaining.isNotBlank() }
                .map { it.remaining }
                .filter { TextNormalizer.tokens(it).any { t -> t !in allLabels } }
            return ExtractedFields(fields.toMap(), unassigned)
        }

        private fun put(field: CardField, value: String, confidence: Double, line: CardLine) {
            // The engine's own confidence in the line lowers ours when it doubts.
            val engine = line.confidence?.let { 0.5 + 0.5 * it.coerceIn(0f, 1f) } ?: 1.0
            fields[field] = ExtractedValue(value.trim(), (confidence * engine).coerceIn(0.0, 1.0).toFloat(), line.original)
        }

        private fun open() = lines.filter { !it.used && it.remaining.isNotBlank() }

        // --- Email ---------------------------------------------------------------------------------------

        private val email = Regex("[\\p{L}0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

        private fun detectEmail() {
            for (line in open()) {
                // OCR reads «@» as «(at)», «[at]» or «©», and adds spaces around it.
                val repaired = line.remaining
                    .replace(Regex("\\s*[(\\[]\\s*at\\s*[)\\]]\\s*", RegexOption.IGNORE_CASE), "@")
                    .replace(Regex("(?<=\\S)\\s*[©®]\\s*(?=\\S)"), "@")
                    .replace(Regex("\\s*@\\s*"), "@")
                val matches = email.findAll(repaired).map { it.value.trimEnd('.') }.toList()
                if (matches.isEmpty()) continue
                line.remaining = repaired
                for (address in matches) {
                    if (CardField.EMAIL !in fields) put(CardField.EMAIL, address.lowercase(), 0.95, line) else extraLines += address
                    line.take(address, allLabels)
                }
            }
        }

        private fun emailDomain(): String? = fields[CardField.EMAIL]?.value?.substringAfter('@')

        // --- Website -------------------------------------------------------------------------------------

        private val web = Regex("(?i)(?<![@\\w.])((?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:/[^\\s,;]*)?)")

        private fun detectWebsite() {
            val emailDomain = emailDomain()
            for (line in open()) {
                for (match in web.findAll(line.remaining).map { it.value }.toList()) {
                    val host = host(match)
                    val explicit = match.contains("www.", ignoreCase = true) || match.startsWith("http", ignoreCase = true)
                    val alone = TextNormalizer.tokens(line.remaining.replace(match, " ")).all { it in allLabels }
                    val sameAsEmail = emailDomain != null && host == emailDomain.lowercase()
                    if (!(explicit || alone || sameAsEmail) || host in dict.publicEmailDomains || host.substringAfterLast('.').length > 6) continue
                    val value = match.trimEnd('/', '.').lowercase()
                    if (CardField.WEBSITE !in fields) put(CardField.WEBSITE, value, if (explicit) 0.95 else 0.85, line) else extraLines += value
                    line.take(match, allLabels)
                }
            }
        }

        private fun host(url: String) = url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')

        // --- Country -------------------------------------------------------------------------------------

        private val postalLetter = Regex("\\b([A-Z]{1,3})-\\s?\\d{4,5}\\b")

        /** A country written on the card, or the letter prefix of a postal code (`D-20457`). */
        private fun detectNamedCountry() {
            var best: Triple<CountryEntry, Double, CardLine>? = null
            for (line in open()) {
                val tokens = line.tokens
                val segments = line.remaining.split(',').map { it.trim().trimEnd('.').replace(".", "") }
                for (country in dict.countries) {
                    val alias = country.aliases.filter { containsSequence(tokens, it) }.maxByOrNull { it.size }
                    val code = country.codes.any { it == line.remaining.trim().replace(".", "") || it == segments.last() }
                    val confidence = when {
                        alias != null && alias.size == tokens.size -> 0.92
                        alias != null && endsWithSequence(tokens, alias) -> 0.85
                        alias != null -> 0.6
                        code -> 0.8
                        else -> continue
                    }
                    if (best == null || confidence > best.second) best = Triple(country, confidence, line)
                }
                postalLetter.find(line.remaining)?.let { m ->
                    dict.postalLetterPrefixes[m.groupValues[1]]?.let { name ->
                        val country = dict.country(name)
                        if (country != null && (best == null || 0.7 > best!!.second)) best = Triple(country, 0.7, line)
                    }
                }
            }
            val (country, confidence, line) = best ?: return
            put(CardField.COUNTRY, country.name, confidence, line)
            countryLanguage = country.language
            // A line that only names the country is used up; inside an address it stays for the address.
            if (confidence >= 0.92 || line.remaining.trim().replace(".", "") in country.codes) line.consume()
        }

        /** Without a named country, the international prefix of a phone is a hint (docs/04). */
        private fun detectCountryHints() {
            if (CardField.COUNTRY in fields) return
            val phone = listOfNotNull(fields[CardField.PHONE], fields[CardField.MOBILE]).firstOrNull { it.value.startsWith("+") } ?: return
            val name = phones.countryOf(phone.value) ?: return
            val country = dict.country(name) ?: return
            fields[CardField.COUNTRY] = ExtractedValue(country.name, 0.6f, phone.sourceLine)
            countryLanguage = country.language
        }

        // --- Phones --------------------------------------------------------------------------------------

        private fun detectPhones() {
            val country = fields[CardField.COUNTRY]?.value ?: guessLanguage()?.let { lang -> dict.countries.firstOrNull { it.language == lang }?.name }
            for (line in open()) {
                for (phone in phones.find(line.remaining)) {
                    val digits = phone.normalized.count { it.isDigit() }
                    val alone = line.remaining.replace(phone.raw, " ").none { it.isLetter() }
                    if (phone.kind == null && !phone.normalized.startsWith("+") && digits < 9 && !alone) continue
                    line.take(phone.raw, allLabels)
                    val kind = when (phone.kind) {
                        LabelKind.FAX -> continue // kept in rawOcrText only
                        LabelKind.MOBILE, LabelKind.PHONE -> phone.kind
                        else -> if (phones.isMobile(phone, country)) LabelKind.MOBILE else LabelKind.PHONE
                    }
                    val field = if (kind == LabelKind.MOBILE) CardField.MOBILE else CardField.PHONE
                    val confidence = if (phone.kind != null) 0.9 else 0.7
                    if (field !in fields) put(field, phone.normalized, confidence, line) else extraLines += phone.normalized
                }
                // A line left with only a label («Fax:») is used up.
                if (line.tokens.all { it in allLabels }) line.consume()
            }
        }

        // --- Address -------------------------------------------------------------------------------------

        private val germanStreetEnd = listOf("strasse", "str", "weg", "platz", "allee", "ring", "damm", "gasse", "ufer", "chaussee")
        private val houseNumber = Regex("(^\\d{1,4}[a-zA-Z]?\\s)|(\\s\\d{1,4}[a-zA-Z]?$)|(\\s\\d{1,4}[a-zA-Z]?,)")

        private fun hasPostalCode(text: String) = dict.postalCodes.any { it.containsMatchIn(text) } || "cedex" in TextNormalizer.tokens(text)

        private fun hasStreetWord(tokens: List<String>) =
            tokens.any { it in dict.streetWords } || tokens.any { t -> germanStreetEnd.any { t.length > it.length + 2 && t.endsWith(it) } }

        /**
         * «Avda. del Puerto 45», «12 Dockside Road»: a street word with a number. A bare house number («Am
         * Kaiserkai 5») only counts next to a postcode line; otherwise «Hall 3» would be an address.
         */
        private fun isStreet(line: CardLine): Boolean {
            if (line.remaining.none { it.isDigit() } || line.tokens.size > 8) return false
            if (hasStreetWord(line.tokens)) return true
            val neighbourPostcode = listOf(line.index - 1, line.index + 1).any { i -> lines.getOrNull(i)?.let { hasPostalCode(it.remaining) } == true }
            return neighbourPostcode && houseNumber.containsMatchIn(line.remaining.trim())
        }

        /** «Veracruz, México», «Dubai, UAE»: a place followed by the country (name or code). */
        private fun endsWithCountry(line: CardLine): Boolean {
            if (line.tokens.size < 2) return false
            val lastSegment = line.remaining.substringAfterLast(',', "").trim().trimEnd('.').replace(".", "")
            return dict.countries.any { c ->
                lastSegment in c.codes || c.aliases.any { a -> line.tokens.size > a.size && endsWithSequence(line.tokens, a) }
            }
        }

        private fun addressSignals(line: CardLine): Int {
            var signals = 0
            if (hasPostalCode(line.remaining)) signals++
            if (isStreet(line)) signals++
            // «Casablanca, Maroc» under «Port de Casablanca, Bd des Almohades».
            val above = lines.getOrNull(line.index - 1)
            if (signals == 0 && endsWithCountry(line) && (hasStreetWord(line.tokens) || (above != null && hasStreetWord(above.tokens)))) signals++
            return signals
        }

        private fun isAvailable(line: CardLine?) = line != null && !line.used && line.remaining.isNotBlank() && !hasStrongTitle(line.tokens)

        private fun detectAddress() {
            val start = open().firstOrNull { addressSignals(it) > 0 && !hasStrongTitle(it.tokens) } ?: return
            val group = ArrayDeque(listOf(start))
            // Lines before: street or place lines without a postcode («Zona Portuale», «Port de Casablanca, Bd …»).
            var previous = lines.getOrNull(start.index - 1)
            while (isAvailable(previous) && previous != null && group.size < 4 && previous.block == start.block &&
                previous.remaining.none { it == '@' } && (isStreet(previous) || hasStreetWord(previous.tokens)) && !isLegalName(previous.tokens)
            ) {
                group.addFirst(previous)
                previous = lines.getOrNull(previous.index - 1)
            }
            // Lines after: postcode + city, a city alone after a postcode, «City, Country».
            var next = lines.getOrNull(start.index + 1)
            while (isAvailable(next) && next != null && group.size < 4 &&
                (addressSignals(next) > 0 || endsWithCountry(next) || isCityLine(next, group.last()))
            ) {
                group.addLast(next)
                next = lines.getOrNull(next.index + 1)
            }
            val strength = group.sumOf { addressSignals(it) }
            val text = group.joinToString(", ") { it.remaining.trim().trimEnd(',') }
            put(CardField.ADDRESS, text, if (strength >= 2) 0.85 else 0.65, start)
            group.forEach { it.consume() }
        }

        private fun isCountryLine(line: CardLine) = dict.countries.any { c -> c.aliases.any { it == line.tokens } }

        /** «Madrid» after «C/ Mayor 12, 28001»: a short line of words right after a postcode. */
        private fun isCityLine(line: CardLine, previous: CardLine) =
            hasPostalCode(previous.remaining) && line.tokens.size in 1..3 && line.remaining.none { it.isDigit() || it == '@' } && !isCountryLine(line)

        // --- Job title -----------------------------------------------------------------------------------

        private fun termCount(tokens: List<String>, terms: List<List<String>>) = terms.count { containsSequence(tokens, it) }

        private fun hasStrongTitle(tokens: List<String>) =
            termCount(tokens, dict.strongTitles) > 0 || tokens.any { t -> dict.titleSuffixes.any { t.length > it.length + 2 && t.endsWith(it) } }

        private fun isTitleTerm(token: String) = dict.strongTitles.any { it == listOf(token) } ||
            dict.weakTitles.any { it == listOf(token) } ||
            dict.titleSuffixes.any { token.length > it.length + 2 && token.endsWith(it) }

        private val connectors =
            setOf("and", "y", "e", "et", "und", "of", "de", "del", "des", "der", "di", "della", "for", "para", "pour", "fur", "per", "en", "in", "dpto", "dept")

        /** Strong terms, or two weak ones, or a short line made only of title words («Comercial», «Export Sales»). */
        private fun titleScore(tokens: List<String>): Int {
            if (tokens.isEmpty() || tokens.size > 9) return 0
            val strong = termCount(tokens, dict.strongTitles) + tokens.count { t -> dict.titleSuffixes.any { t.length > it.length + 2 && t.endsWith(it) } }
            val weak = termCount(tokens, dict.weakTitles)
            val onlyTitleWords = tokens.size <= 3 && tokens.all { isTitleTerm(it) || it in connectors }
            return when {
                strong > 0 -> 2 * strong + weak
                weak >= 2 || onlyTitleWords -> weak + 1
                else -> 0
            }
        }

        private fun detectJobTitle() {
            data class Candidate(val line: CardLine, val text: String, val score: Int)
            val candidates = open().mapNotNull { line ->
                if (line.remaining.count { it.isDigit() } > 2 || isLegalName(line.tokens)) return@mapNotNull null
                // «Ana Ruiz, Sales Manager»: the title is the part after the comma; the name stays.
                val afterComma = line.remaining.substringAfter(',', "").trim()
                val beforeComma = line.remaining.substringBefore(',').trim()
                if (afterComma.isNotEmpty() && titleScore(TextNormalizer.tokens(afterComma)) > 0 && titleScore(TextNormalizer.tokens(beforeComma)) == 0) {
                    Candidate(line, afterComma, titleScore(TextNormalizer.tokens(afterComma)))
                } else {
                    titleScore(line.tokens).takeIf { it > 0 }?.let { Candidate(line, line.remaining, it) }
                }
            }
            val best = candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { -it.line.index }) ?: return
            var text = best.text
            // A title may continue on the next line: «Director of Sales» / «& Marketing».
            val next = lines.getOrNull(best.line.index + 1)
            val continues = next != null && !next.used && next.block == best.line.block && next.remaining.none { it.isDigit() } &&
                (titleScore(next.tokens) > 0 || next.remaining.trimStart().firstOrNull()?.let { it == '&' || it.isLowerCase() } == true) &&
                next.tokens.size <= 5
            if (continues) {
                text = "$text ${next.remaining}"
                next.consume()
            }
            put(CardField.JOB_TITLE, text.trim().trimEnd(','), min(0.92, 0.65 + 0.08 * best.score), best.line)
            if (text == best.line.remaining || best.text == best.line.remaining) {
                best.line.consume()
            } else {
                best.line.remaining = best.line.remaining.substringBefore(',').trim()
            }
            titleLine = best.line
        }

        // --- Company -------------------------------------------------------------------------------------

        private fun isLegalName(tokens: List<String>) = dict.legalSuffixes.any { s -> tokens.size > s.size && endsWithSequence(tokens, s) }

        private fun domainLabel(): String? {
            val host = fields[CardField.WEBSITE]?.value?.let { host(it) }
                ?: emailDomain()?.takeIf { it !in dict.publicEmailDomains }
            return host?.substringBeforeLast('.')?.substringAfterLast('.')?.filter { it.isLetterOrDigit() }?.takeIf { it.length >= 3 }
        }

        private fun matchesDomain(tokens: List<String>, label: String?): Boolean {
            if (label == null) return false
            val compact = tokens.joinToString("")
            val significant = tokens.filter { it.length >= 3 }
            return compact.isNotEmpty() && (
                compact.contains(label) || label.contains(compact) ||
                    significant.any { it.length >= 4 && label.contains(it) } ||
                    (tokens.size >= 2 && tokens.joinToString("") { it.take(1) } == label) ||
                    prefixesSpell(tokens, label)
                )
        }

        /** True when [label] is made of prefixes (≥ 2 letters) of consecutive tokens: `atlprop`, `nordfjord`. */
        private fun prefixesSpell(tokens: List<String>, label: String): Boolean {
            fun spell(from: Int, rest: String): Boolean {
                if (rest.isEmpty()) return from >= 2 || tokens.size == 1
                if (from >= tokens.size) return false
                val token = tokens[from]
                return (2..minOf(token.length, rest.length)).any { n -> token.take(n) == rest.take(n) && spell(from + 1, rest.drop(n)) }
            }
            return label.length >= 4 && (tokens.indices).any { spell(it, label) }
        }

        private fun emailLocalTokens(): List<String> = fields[CardField.EMAIL]?.value?.substringBefore('@')
            ?.split('.', '_', '-')?.map { TextNormalizer.fold(it) }?.filter { it.isNotEmpty() }.orEmpty()

        /** True when the line looks like the person of the email: «ana.ruiz@…», «aruiz@…», «anaruiz@…». */
        private fun matchesEmailPerson(tokens: List<String>): Boolean {
            val local = emailLocalTokens()
            if (local.isEmpty() || tokens.size < 2) return false
            val joined = local.joinToString("")
            if (local.any { l -> l.length >= 3 && l in tokens }) return true
            val first = tokens.first()
            val last = tokens.last()
            val initials = tokens.joinToString("") { it.take(1) }
            return joined == first + last || joined == first.take(1) + last || joined == last + first.take(1) || joined == initials ||
                (joined.length >= 4 && tokens.any { it.length >= 4 && joined.contains(it) })
        }

        private fun detectCompany() {
            val label = domainLabel()
            data class Candidate(val line: CardLine, val score: Double)
            val candidates = open().mapNotNull { line ->
                val tokens = line.tokens
                if (tokens.isEmpty() || tokens.size > 8 || line.remaining.contains('@')) return@mapNotNull null
                if (line.remaining.count { it.isDigit() } > 4) return@mapNotNull null
                var score = 0.0
                if (isLegalName(tokens)) score += 4
                if (matchesDomain(tokens, label)) score += 3
                if (line.height >= medianHeight * 1.25f) score += 1.5
                if (line.index < max(2, lines.size / 3)) score += 0.5
                val letters = line.remaining.filter { it.isLetter() }
                if (letters.length >= 4 && letters.all { it.isUpperCase() }) score += 0.5
                if (lines.count { it.block == line.block } == 1) score += 0.5
                if (hasStrongTitle(tokens)) score -= 4
                if (matchesEmailPerson(tokens) && !isLegalName(tokens)) score -= 5
                Candidate(line, score)
            }.sortedByDescending { it.score }
            val best = candidates.firstOrNull()?.takeIf { it.score >= 2.0 } ?: return
            val margin = best.score - (candidates.getOrNull(1)?.score ?: 0.0)
            val confidence = (0.4 + 0.1 * best.score).coerceAtMost(0.95) - if (margin < 1.0) 0.15 else 0.0
            put(CardField.COMPANY, best.line.remaining.trim().trimEnd(','), confidence, best.line)
            best.line.consume()
        }

        /** The language most marker words point to, if clearly ahead (used only to split names). */
        private fun guessLanguage(): String? {
            val tokens = lines.flatMap { TextNormalizer.tokens(it.original) }
            val scores = dict.languageMarkers.mapValues { (_, words) -> tokens.count { it in words } }.entries.sortedByDescending { it.value }
            val first = scores.firstOrNull() ?: return null
            val second = scores.getOrNull(1)?.value ?: 0
            return first.key.takeIf { first.value >= 1 && first.value > second }
        }

        // --- Name ----------------------------------------------------------------------------------------

        private val nameWord = Regex("^[\\p{Lu}][\\p{L}'’.-]*$")

        private fun looksLikeName(text: String): Boolean {
            val words = text.split(' ').filter { it.isNotEmpty() }
            val (core, _) = names.stripPrefixes(words)
            val particles = dict.nameParticles.flatten().toSet()
            if (core.count { TextNormalizer.fold(it) !in particles } !in 2..4 || text.any { it.isDigit() || it == '@' }) return false
            return core.all { w -> nameWord.matches(w) || TextNormalizer.fold(w) in particles } &&
                core.count { it.any(Char::isLetter) && it.trimEnd('.').length > 1 } >= 2
        }

        private fun detectName() {
            data class Candidate(val line: CardLine, val text: String, val score: Double)
            val candidates = open().mapNotNull { line ->
                val text = line.remaining.trim().trimEnd(',')
                // «DUPONT, Jean» is a name too.
                val plain = text.replace(", ", " ")
                if (!looksLikeName(plain)) return@mapNotNull null
                val tokens = TextNormalizer.tokens(plain)
                if (titleScore(tokens) > 0 || isLegalName(tokens) || isCountryLine(line)) return@mapNotNull null
                var score = 0.0
                if (matchesEmailPerson(tokens)) score += 3
                if (line.height >= medianHeight * 1.15f) score += 1.5
                titleLine?.let { t -> if (kotlin.math.abs(t.index - line.index) == 1) score += 2 }
                if (line.index < lines.size / 2) score += 0.5
                Candidate(line, text, score)
            }
            val best = candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { -it.line.index }) ?: return
            val split = names.split(best.text, countryLanguage ?: guessLanguage())
            val confidence = (0.55 + 0.1 * best.score).coerceAtMost(0.95)
            if (split.first.isNotEmpty()) put(CardField.FIRST_NAME, split.first, confidence, best.line)
            if (split.last.isNotEmpty()) put(CardField.LAST_NAME, split.last, confidence, best.line)
            split.prefix?.let { put(CardField.NOTES, it, 0.9, best.line) }
            best.line.consume()
        }
    }

    private companion object {
        fun containsSequence(tokens: List<String>, sequence: List<String>): Boolean = sequence.isNotEmpty() && tokens.size >= sequence.size &&
            (0..tokens.size - sequence.size).any { i -> tokens.subList(i, i + sequence.size) == sequence }

        fun endsWithSequence(tokens: List<String>, sequence: List<String>): Boolean = tokens.size >= sequence.size && tokens.takeLast(sequence.size) == sequence
    }
}

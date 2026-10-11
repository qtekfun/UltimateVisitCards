package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.ocr.NormRect
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrBlock
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrLine
import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult

/** A synthetic card: what the OCR «read» on each side and the fields a person would fill in. */
data class CorpusCase(val id: String, val design: String, val front: OcrResult, val back: OcrResult?, val expected: Map<CardField, String>)

/**
 * Reads the synthetic corpus (the `.txt` files in `src/test/resources/corpus`). A compact text format instead of raw
 * `OcrResult` JSON, so cases stay readable and reviewable (see docs/decisions.md):
 *
 * ```
 * === es-01 | design note
 * [front]
 * 26 COMPANY S.L.          ← line height in px, then the text
 * --                       ← block separator
 * 20 Name Surname
 * [back]                   ← optional
 * [expected]
 * firstName = Name         ← keys: CardField in camelCase; a missing key means «must stay empty»
 * ```
 */
object CardCorpus {
    private val files = listOf("cards_es.txt", "cards_en.txt", "cards_fr.txt", "cards_de.txt", "cards_it.txt")

    fun load(): List<CorpusCase> = files.flatMap { parse(read(it)) }

    private fun read(file: String): String =
        requireNotNull(CardCorpus::class.java.getResourceAsStream("/corpus/$file")) { "Missing corpus $file" }.bufferedReader().readText()

    fun parse(text: String): List<CorpusCase> = text.split(Regex("(?m)^=== ")).drop(1).map { chunk ->
        val lines = chunk.lines()
        val (id, design) = lines.first().split('|').map { it.trim() }.let { it[0] to it.getOrElse(1) { "" } }
        val sections = mutableMapOf<String, MutableList<String>>()
        var current = ""
        for (line in lines.drop(1)) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#") || trimmed.isEmpty() -> Unit
                trimmed.startsWith("[") && trimmed.endsWith("]") -> current = trimmed.removeSurrounding("[", "]")
                else -> sections.getOrPut(current) { mutableListOf() } += trimmed
            }
        }
        CorpusCase(
            id = id,
            design = design,
            front = ocr(sections["front"].orEmpty()),
            back = sections["back"]?.let { ocr(it) },
            expected = sections["expected"].orEmpty().associate { entry ->
                val key = entry.substringBefore('=').trim()
                field(key) to entry.substringAfter('=').trim()
            }
        )
    }

    private fun field(camel: String): CardField = CardField.valueOf(camel.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase())

    /** Lines laid out top to bottom, 1/20 of the card apart; blocks as written. */
    private fun ocr(lines: List<String>): OcrResult {
        val blocks = mutableListOf<OcrBlock>()
        var current = mutableListOf<OcrLine>()
        var y = 0.05f
        fun flush() {
            if (current.isNotEmpty()) {
                blocks += OcrBlock(current.joinToString("\n") { it.text }, current, NormRect(0.05f, current.first().box.top, 0.95f, current.last().box.bottom))
                current = mutableListOf()
            }
        }
        for (line in lines) {
            if (line == "--") {
                flush()
                continue
            }
            val height = line.substringBefore(' ').toFloat()
            val text = line.substringAfter(' ')
            current += OcrLine(text, NormRect(0.05f, y, 0.95f, y + height / 400f), confidence = null, heightPx = height)
            y += 0.05f
        }
        flush()
        return OcrResult(blocks.joinToString("\n") { it.text }, blocks)
    }
}

package com.qtekfun.ultimatevisitcards.domain.extract

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Field accuracy over the synthetic corpus (≥ 60 cards, 5 languages). A field counts as right when the
 * proposed value equals the expected one, including «empty» when nothing should be proposed (never invent).
 * The full report with every mismatch is written to `build/reports/extraction/corpus.md`.
 */
class CardFieldExtractorCorpusTest {
    private val extractor = CardFieldExtractor()
    private val cases = CardCorpus.load()

    /**
     * Minimum accuracy per field. The rules were tuned on this corpus (it reaches 100 %), so this is a
     * regression guard, not a measure of real-world accuracy: that is measured with photos (docs/07-fases.md).
     */
    private val minimum = CardField.entries.associateWith { 0.95 }

    @Test
    fun `the corpus has at least 60 cards in the five languages`() {
        assertTrue(cases.size >= 60, "only ${cases.size} cases")
        listOf("es", "en", "fr", "de", "it").forEach { lang -> assertTrue(cases.count { it.id.startsWith("$lang-") } >= 10, lang) }
        assertTrue(cases.map { it.id }.toSet().size == cases.size, "duplicate case ids")
    }

    @Test
    fun `every field reaches its minimum accuracy`() {
        val mismatches = mutableListOf<String>()
        val right = CardField.entries.associateWith { 0 }.toMutableMap()
        for (case in cases) {
            val result = extractor.extract(case.front, case.back)
            for (field in CardField.entries) {
                val expected = case.expected[field]
                val actual = result.value(field)
                if (expected == actual) {
                    right[field] = right.getValue(field) + 1
                } else {
                    mismatches += "| ${case.id} | $field | ${expected ?: "—"} | ${actual ?: "—"} |"
                }
            }
        }
        val accuracy = CardField.entries.associateWith { right.getValue(it).toDouble() / cases.size }
        report(accuracy, mismatches)
        val failing = minimum.filter { (field, min) -> accuracy.getValue(field) < min }
        assertTrue(
            failing.isEmpty(),
            "Below minimum: " + failing.keys.joinToString { "$it ${"%.2f".format(accuracy.getValue(it))}" } + "\n" + mismatches.joinToString("\n")
        )
    }

    private fun report(accuracy: Map<CardField, Double>, mismatches: List<String>) {
        val dir = File(System.getProperty("user.dir"), "build/reports/extraction").apply { mkdirs() }
        File(dir, "corpus.md").writeText(
            buildString {
                appendLine("# Extracción sobre el corpus sintético (${cases.size} tarjetas)")
                appendLine()
                appendLine("| Campo | Acierto |")
                appendLine("|---|---|")
                accuracy.forEach { (field, value) -> appendLine("| $field | ${"%.1f".format(value * 100)} % |") }
                appendLine()
                appendLine("| Caso | Campo | Esperado | Obtenido |")
                appendLine("|---|---|---|---|")
                mismatches.forEach { appendLine(it) }
            }
        )
    }
}

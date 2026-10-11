package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import kotlin.test.assertTrue
import org.junit.Test

/**
 * «Never assume fixed positions» (docs/04): the same cards with their blocks in reverse order (company at the
 * bottom, contact data on top) must still be read. Pattern fields must not move at all; layout-sensitive
 * ones (name, company, title) may lose a little.
 */
class LayoutRobustnessTest {
    private val extractor = CardFieldExtractor()

    private fun reversed(result: OcrResult): OcrResult {
        val blocks = result.blocks.asReversed()
        // Re-stack the boxes so the reversed order is also the visual order.
        var y = 0.05f
        val restacked = blocks.map { block ->
            val lines = block.lines.map { line ->
                val h = line.box.height
                line.copy(box = line.box.copy(top = y, bottom = y + h)).also { y += 0.05f }
            }
            block.copy(lines = lines, box = block.box.copy(top = lines.first().box.top, bottom = lines.last().box.bottom))
        }
        return result.copy(blocks = restacked)
    }

    @Test
    fun `reversing the layout keeps the fields`() {
        val cases = CardCorpus.load()
        val right = mutableMapOf<CardField, Int>()
        val misses = mutableListOf<String>()
        for (case in cases) {
            val result = extractor.extract(reversed(case.front), case.back?.let { reversed(it) })
            for (field in CardField.entries) {
                if (result.value(field) ==
                    case.expected[field]
                ) {
                    right.merge(field, 1, Int::plus)
                } else {
                    misses += "${case.id} $field: ${case.expected[field]} ≠ ${result.value(field)}"
                }
            }
        }
        val accuracy = CardField.entries.associateWith { (right[it] ?: 0).toDouble() / cases.size }
        val patterns = listOf(CardField.EMAIL, CardField.WEBSITE, CardField.PHONE, CardField.MOBILE, CardField.COUNTRY)
        patterns.forEach { assertTrue(accuracy.getValue(it) >= 0.95, "$it ${accuracy[it]}\n${misses.joinToString("\n")}") }
        CardField.entries.forEach { assertTrue(accuracy.getValue(it) >= 0.8, "$it ${accuracy[it]}\n${misses.joinToString("\n")}") }
    }
}

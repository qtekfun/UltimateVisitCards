package com.qtekfun.ultimatevisitcards.domain.extract

import com.qtekfun.ultimatevisitcards.domain.ocr.OcrResult
import com.qtekfun.ultimatevisitcards.domain.text.TextNormalizer
import java.text.Normalizer

/**
 * One line of the card being read. Detectors take what they recognise out of [remaining]; a line is
 * [used] when nothing meaningful is left in it.
 */
internal class CardLine(val index: Int, val original: String, val block: Int, val height: Float, val confidence: Float?) {
    var remaining: String = original
    var used: Boolean = false

    val tokens: List<String> get() = TextNormalizer.tokens(remaining)

    /** Removes [part] from [remaining]; marks the line used when only separators and labels are left. */
    fun take(part: String, labels: Set<String>) {
        remaining = remaining.replaceFirst(part, " ").replace(SPACES, " ").trim()
        val leftover = TextNormalizer.tokens(remaining).filterNot { it in labels }
        if (leftover.isEmpty()) {
            remaining = ""
            used = true
        }
    }

    fun consume() {
        remaining = ""
        used = true
    }

    companion object {
        private val SPACES = Regex("\\s+")
    }
}

internal object CardLines {
    private val oddSpaces = Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000\\t]")
    private val dashes = Regex("[\\u2010-\\u2015\\u2212]")
    private val spaces = Regex(" {2,}")

    // Separators that join independent fields on one printed line: «Ana Ruiz | Sales Manager».
    private val fieldSeparators = Regex("\\s+[|·•]\\s+|\\s{3,}")

    /** NFC, ordinary spaces and hyphens, single spaces. */
    fun clean(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .replace(oddSpaces, " ")
        .replace(dashes, "-")
        .replace(spaces, " ")
        .trim()

    /**
     * The card's lines in reading order: front first, then the back without the lines it repeats (the back
     * often carries the same card in another language).
     */
    fun collect(front: OcrResult, back: OcrResult?): List<CardLine> {
        val lines = mutableListOf<CardLine>()
        val seen = mutableSetOf<String>()
        var block = 0
        for (side in listOfNotNull(front, back)) {
            val blocks = side.blocks.sortedWith(compareBy({ it.box.top }, { it.box.left }))
            for (b in blocks) {
                for (line in b.lines) {
                    for (piece in clean(line.text).split(fieldSeparators)) {
                        val text = piece.trim()
                        val key = TextNormalizer.fold(text)
                        if (key.isEmpty() || !seen.add(key)) continue
                        lines += CardLine(lines.size, text, block, line.heightPx, line.confidence)
                    }
                }
                block++
            }
        }
        return lines
    }
}

package com.reader343.pdf

import com.reader343.domain.NormRect
import com.reader343.domain.SpeechUnit
import java.text.BreakIterator
import java.util.Locale

object TextSegmenter {

    private const val EDGE_LINES = 2
    private const val MIN_SIGNATURE = 3
    private const val LINE_OVERLAP = 0.3f
    const val MAX_UNIT_CHARS = 500

    private val pageNumber = Regex(
        """^[\p{Pd}\s]*(?:(?:page|p\.)\s*)?(?:\p{Nd}+|[ivxlcdm]{1,7})(?:\s*(?:of|/)\s*\p{Nd}+)?[\p{Pd}\s]*$""",
        RegexOption.IGNORE_CASE,
    )

    fun segment(page: PageText, locale: Locale = Locale.getDefault(), neighbors: List<PageText> = emptyList()): List<SpeechUnit> {
        val lines = contentLines(page.chars)
        if (lines.isEmpty()) return emptyList()
        val neighborSignatures = neighbors.flatMap { neighbor ->
            edges(contentLines(neighbor.chars)).mapNotNull { signature(neighbor.chars, it) }
        }.toSet()
        val edgeLines = edges(lines).toSet()
        val kept = lines.filterNot { line ->
            line in edgeLines && (isPageNumber(page.chars, line) || signature(page.chars, line) in neighborSignatures)
        }
        val (text, map) = clean(page.chars, kept)
        return sentences(text, locale).flatMap { split(text, it) }
            .mapNotNull { range -> unitFor(page, text, map, range) }
            .mapIndexed { index, unit -> unit.copy(sentenceIndex = index) }
    }

    private fun contentLines(chars: List<TextChar>): List<IntRange> =
        lines(chars).filter { line -> line.any { chars[it].char.isLetterOrDigit() } }

    private fun lines(chars: List<TextChar>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = -1
        var lastBox: NormRect? = null
        for (i in chars.indices) {
            val c = chars[i]
            if (c.char == '\n' || c.char == '\r') {
                if (start >= 0) result += start until i
                start = -1
                lastBox = null
                continue
            }
            val box = c.box
            if (start >= 0 && box != null && lastBox != null && startsNewLine(lastBox, box)) {
                result += start until i
                start = -1
            }
            if (start < 0) start = i
            if (box != null) lastBox = box
        }
        if (start >= 0) result += start until chars.size
        return result
    }

    private fun startsNewLine(previous: NormRect, box: NormRect): Boolean {
        val overlap = minOf(previous.bottom, box.bottom) - maxOf(previous.top, box.top)
        return overlap < minOf(previous.height, box.height) * LINE_OVERLAP
    }

    private fun edges(lines: List<IntRange>): List<IntRange> =
        if (lines.size <= EDGE_LINES * 2) lines else lines.take(EDGE_LINES) + lines.takeLast(EDGE_LINES)

    private fun lineText(chars: List<TextChar>, line: IntRange): String =
        line.joinToString("") { chars[it].char.toString() }.trim()

    private fun isPageNumber(chars: List<TextChar>, line: IntRange): Boolean =
        pageNumber.matches(lineText(chars, line))

    private fun signature(chars: List<TextChar>, line: IntRange): String? {
        val builder = StringBuilder()
        for (i in line) {
            val c = chars[i].char
            if (c.isLetter()) {
                builder.append(c.lowercaseChar())
            } else if (c.isWhitespace() && builder.isNotEmpty() && builder.last() != ' ') {
                builder.append(' ')
            }
        }
        return builder.toString().trim().takeIf { it.length >= MIN_SIGNATURE }
    }

    private fun isSoftHyphen(c: Char) = c == '\u00AD' || c == '\u0002'

    private fun isHardHyphen(c: Char) = c == '-' || c == '\u2010'

    private fun isBlank(c: Char) = c.isWhitespace() || c.isISOControl() || c == '\uFFFE' || c == '\uFFFF'

    private fun clean(chars: List<TextChar>, lines: List<IntRange>): Pair<String, IntArray> {
        val builder = StringBuilder()
        val map = ArrayList<Int>()
        fun append(c: Char, index: Int) {
            builder.append(c)
            map += index
        }
        fun space() {
            if (builder.isNotEmpty() && builder.last() != ' ') append(' ', -1)
        }
        lines.forEachIndexed { n, line ->
            for (i in line) {
                val c = chars[i].char
                when {
                    isSoftHyphen(c) -> Unit
                    isBlank(c) -> space()
                    else -> append(c, i)
                }
            }
            val next = lines.getOrNull(n + 1) ?: return@forEachIndexed
            val last = line.lastOrNull { isSoftHyphen(chars[it].char) || !isBlank(chars[it].char) } ?: return@forEachIndexed
            val first = next.firstOrNull { !isBlank(chars[it].char) }?.let { chars[it].char }
            val lastChar = chars[last].char
            val joinsSoft = isSoftHyphen(lastChar)
            val joinsHard = isHardHyphen(lastChar) && first != null && first.isLetter() &&
                chars.getOrNull(last - 1)?.char?.isLetter() == true
            while (builder.isNotEmpty() && builder.last() == ' ') {
                builder.setLength(builder.length - 1)
                map.removeAt(map.lastIndex)
            }
            if (joinsHard && first?.isLowerCase() == true) {
                builder.setLength(builder.length - 1)
                map.removeAt(map.lastIndex)
            }
            if (!joinsSoft && !joinsHard) space()
        }
        return builder.toString() to map.toIntArray()
    }

    private fun sentences(text: String, locale: Locale): List<IntRange> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val result = mutableListOf<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            result += start until end
            start = end
            end = iterator.next()
        }
        return result
    }

    private fun split(text: String, range: IntRange): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = range.first
        while (range.last - start + 1 > MAX_UNIT_CHARS) {
            val limit = start + MAX_UNIT_CHARS
            val cut = text.lastIndexOf(' ', limit).takeIf { it > start } ?: limit
            result += start until cut
            start = cut
        }
        result += start..range.last
        return result
    }

    private fun unitFor(page: PageText, text: String, map: IntArray, range: IntRange): SpeechUnit? {
        var first = range.first
        var last = range.last
        while (first <= last && text[first].isWhitespace()) first++
        while (last >= first && text[last].isWhitespace()) last--
        if (first > last) return null
        val sentence = text.substring(first, last + 1)
        if (sentence.none { it.isLetterOrDigit() }) return null
        val indices = (first..last).map { map[it] }.filter { it >= 0 }
        if (indices.isEmpty()) return null
        return SpeechUnit(
            page = page.page,
            sentenceIndex = 0,
            charStart = indices.first(),
            charEnd = indices.last() + 1,
            text = sentence,
            rects = page.rectsFor(indices),
        )
    }
}

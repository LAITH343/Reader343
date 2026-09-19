package com.reader343.pdf

import com.reader343.domain.NormRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextSegmenterTest {

    private fun page(vararg lines: String, index: Int = 0): PageText {
        val chars = mutableListOf<TextChar>()
        lines.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                val box = if (c == ' ') null else NormRect(col * W, row * H, col * W + W * 0.8f, row * H + H * 0.8f)
                chars += TextChar(c, box)
            }
            if (row < lines.lastIndex) {
                chars += TextChar('\r', null)
                chars += TextChar('\n', null)
            }
        }
        return PageText(index, chars)
    }

    private fun texts(page: PageText, neighbors: List<PageText> = emptyList()) =
        TextSegmenter.segment(page, Locale.ENGLISH, neighbors).map { it.text }

    @Test
    fun splitsIntoOrderedSentences() {
        val units = TextSegmenter.segment(
            page("The sun rose. Birds sang", "loudly today. It was warm.", index = 4),
            Locale.ENGLISH,
        )
        assertEquals(listOf("The sun rose.", "Birds sang loudly today.", "It was warm."), units.map { it.text })
        assertEquals(listOf(0, 1, 2), units.map { it.sentenceIndex })
        assertEquals(listOf("4:0", "4:1", "4:2"), units.map { it.id })
        assertTrue(units.all { it.page == 4 })
    }

    @Test
    fun charRangesAndRectsFollowSourceLines() {
        val source = page("The sun rose. Birds sang", "loudly today. It was warm.")
        val unit = TextSegmenter.segment(source, Locale.ENGLISH)[1]
        assertEquals("Birds sang\r\nloudly today.", source.text.substring(unit.charStart, unit.charEnd))
        assertEquals(2, unit.rects.size)
        assertEquals(14 * W, unit.rects[0].left, EPS)
        assertEquals(0f, unit.rects[0].top, EPS)
        assertEquals(0f, unit.rects[1].left, EPS)
        assertEquals(H, unit.rects[1].top, EPS)
    }

    @Test
    fun joinsHyphenatedLineBreaks() {
        assertEquals(listOf("It was an extraordinary day."), texts(page("It was an extra-", "ordinary day.")))
        assertEquals(listOf("The Anglo-Saxon kings ruled."), texts(page("The Anglo-", "Saxon kings ruled.")))
        assertEquals(listOf("Read the documentation now."), texts(page("Read the document${Char(0xAD)}", "ation now.")))
        assertEquals(listOf("Read the documentation now."), texts(page("Read the document${Char(2)}", "ation now.")))
    }

    @Test
    fun keepsDashesThatAreNotLineBreakHyphens() {
        assertEquals(listOf("Pages 10 - 12 were torn."), texts(page("Pages 10 -", "12 were torn.")))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals(listOf("Too many spaces here."), texts(page("Too    many \t  spaces", "", "   here.")))
    }

    @Test
    fun dropsPageNumbers() {
        val body = arrayOf("First line of the body.", "Second line of it.", "Third line here.")
        assertEquals(body.toList(), texts(page(*body, "42")))
        assertEquals(body.toList(), texts(page("- 7 -", *body)))
        assertEquals(body.toList(), texts(page(*body, "Page 3 of 10")))
        assertEquals(body.toList(), texts(page("xiv", *body)))
    }

    @Test
    fun keepsNumbersInsideBody() {
        val units = texts(page("Line one is here.", "The year was", "1984", "and it rained.", "Line five is here.", "Line six is here."))
        assertTrue(units.contains("The year was 1984 and it rained."))
    }

    @Test
    fun dropsRunningHeadersSeenOnNeighborPages() {
        val current = page("THE GREAT BOOK  12", "Body text starts here.", "More body follows.", index = 12)
        val previous = page("THE GREAT BOOK  11", "Other text on that page.", index = 11)
        val next = page("THE GREAT BOOK  13", "Yet more text.", index = 13)
        assertEquals(listOf("Body text starts here.", "More body follows."), texts(current, listOf(previous, next)))
        assertEquals("THE GREAT BOOK 12 Body text starts here.", texts(current).first())
    }

    @Test
    fun dropsRunningFootersSeenOnNeighborPages() {
        val current = page("Body text here.", "Chapter Two: The Road", index = 5)
        val previous = page("Other text.", "Chapter Two: The Road", index = 4)
        assertEquals(listOf("Body text here."), texts(current, listOf(previous)))
    }

    @Test
    fun droppedHeaderIsExcludedFromRects() {
        val current = page("RUNNING TITLE", "Body text.", index = 2)
        val previous = page("RUNNING TITLE", "Other.", index = 1)
        val unit = TextSegmenter.segment(current, Locale.ENGLISH, listOf(previous)).single()
        assertEquals(1, unit.rects.size)
        assertEquals(H, unit.rects[0].top, EPS)
    }

    @Test
    fun skipsEmptyAndPunctuationOnlyContent() {
        assertEquals(emptyList<String>(), texts(page()))
        assertEquals(emptyList<String>(), texts(page("   ", "* * *", "....")))
        assertEquals(listOf("Before.", "After."), texts(page("Before.", "* * *", "After.")))
    }

    @Test
    fun splitsOverlongSentencesAtWordBoundaries() {
        val words = List(200) { "word$it" }.joinToString(" ")
        val units = TextSegmenter.segment(page(words), Locale.ENGLISH)
        assertTrue(units.size > 1)
        assertTrue(units.all { it.text.length <= TextSegmenter.MAX_UNIT_CHARS })
        assertEquals(words, units.joinToString(" ") { it.text })
        assertTrue(units.zipWithNext().all { (a, b) -> a.charEnd <= b.charStart })
    }

    @Test
    fun detectsLineBreaksFromBoxesWithoutNewlines() {
        val chars = mutableListOf<TextChar>()
        listOf("First line.", "Second line.").forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                chars += TextChar(c, if (c == ' ') null else NormRect(col * W, row * H, col * W + W, row * H + H * 0.8f))
            }
        }
        val units = TextSegmenter.segment(PageText(0, chars), Locale.ENGLISH)
        assertEquals(listOf("First line.", "Second line."), units.map { it.text })
    }

    @Test
    fun segmentsArabicText() {
        val units = texts(page("كان يوماً مشرقاً. والساعات تدق", "الثالثة عشرة."))
        assertEquals(listOf("كان يوماً مشرقاً.", "والساعات تدق الثالثة عشرة."), units)
    }

    private companion object {
        const val W = 0.01f
        const val H = 0.03f
        const val EPS = 1e-5f
    }
}

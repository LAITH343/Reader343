package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarksTest {

    private val rect = NormRect(0f, 0f, 1f, 0.1f)

    private fun highlight(id: Long, page: Int, createdAt: Long, snippet: String? = "quote $id") = Highlight(
        id = id,
        page = page,
        rects = listOf(rect),
        color = 0,
        charStart = null,
        charEnd = null,
        snippet = snippet,
        createdAt = createdAt,
    )

    private fun note(id: Long, page: Int, createdAt: Long, highlightId: Long? = null, snippet: String? = null) = Note(
        id = id,
        page = page,
        anchor = NoteAnchor(rect = rect, highlightId = highlightId, snippet = snippet),
        body = "note $id",
        createdAt = createdAt,
    )

    @Test
    fun attachesNoteToItsHighlight() {
        val marks = buildMarks(
            highlights = listOf(highlight(1, page = 4, createdAt = 100)),
            notes = listOf(note(7, page = 4, createdAt = 150, highlightId = 1)),
        )
        assertEquals(1, marks.size)
        assertEquals(7L, marks.single().note?.id)
        assertEquals("quote 1", marks.single().quote)
    }

    @Test
    fun keepsNotesWithoutHighlightSeparate() {
        val marks = buildMarks(
            highlights = listOf(highlight(1, page = 4, createdAt = 100)),
            notes = listOf(
                note(7, page = 2, createdAt = 50, snippet = "selected"),
                note(8, page = 3, createdAt = 60, highlightId = 99),
            ),
        )
        assertEquals(listOf("h1", "n8", "n7"), marks.map { it.key })
        assertEquals("selected", marks.last().quote)
        assertNull(marks.last().color)
    }

    @Test
    fun sortsNewestFirstUsingLatestActivity() {
        val marks = buildMarks(
            highlights = listOf(
                highlight(1, page = 1, createdAt = 100),
                highlight(2, page = 2, createdAt = 200),
            ),
            notes = listOf(note(5, page = 1, createdAt = 300, highlightId = 1)),
        )
        assertEquals(listOf("h1", "h2"), marks.map { it.key })
    }

    @Test
    fun filtersByKind() {
        val marks = buildMarks(
            highlights = listOf(
                highlight(1, page = 1, createdAt = 100),
                highlight(2, page = 2, createdAt = 200),
            ),
            notes = listOf(
                note(5, page = 1, createdAt = 50, highlightId = 1),
                note(6, page = 9, createdAt = 10),
            ),
        )
        assertEquals(3, marks.filteredBy(MarkFilter.All).size)
        assertEquals(listOf("h2", "h1"), marks.filteredBy(MarkFilter.Highlights).map { it.key })
        assertEquals(listOf("h1", "n6"), marks.filteredBy(MarkFilter.Notes).map { it.key })
    }
}

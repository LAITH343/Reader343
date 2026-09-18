package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutlineTest {

    private val outline = listOf(
        OutlineEntry("Preface", 2, 0),
        OutlineEntry("Chapter 1", 10, 0),
        OutlineEntry("Section 1.1", 10, 1),
        OutlineEntry("Section 1.2", 15, 1),
        OutlineEntry("Chapter 2", 30, 0),
    )

    @Test
    fun noChapterBeforeFirstEntry() {
        assertNull(outline.chapterAt(page = 1, pageCount = 50))
    }

    @Test
    fun emptyOutlineHasNoChapter() {
        assertNull(emptyList<OutlineEntry>().chapterAt(page = 5, pageCount = 50))
    }

    @Test
    fun lastEntryOnSamePageWins() {
        val chapter = outline.chapterAt(page = 10, pageCount = 50)
        assertEquals(Chapter(index = 2, title = "Section 1.1", startPage = 10, endPage = 15), chapter)
    }

    @Test
    fun chapterEndsAtNextEntry() {
        val chapter = outline.chapterAt(page = 20, pageCount = 50)!!
        assertEquals("Section 1.2", chapter.title)
        assertEquals(30, chapter.endPage)
        assertEquals(10, chapter.pagesLeft(20))
    }

    @Test
    fun lastChapterEndsAtBookEnd() {
        val chapter = outline.chapterAt(page = 45, pageCount = 50)!!
        assertEquals("Chapter 2", chapter.title)
        assertEquals(50, chapter.endPage)
        assertEquals(5, chapter.pagesLeft(45))
    }

    @Test
    fun unorderedOutlineUsesHighestStartBelowPage() {
        val unordered = listOf(
            OutlineEntry("Appendix", 40, 0),
            OutlineEntry("Intro", 0, 0),
            OutlineEntry("Body", 20, 0),
        )
        val chapter = unordered.chapterAt(page = 25, pageCount = 60)!!
        assertEquals("Body", chapter.title)
        assertEquals(2, chapter.index)
        assertEquals(40, chapter.endPage)
    }

    @Test
    fun pagesLeftIsAtLeastOne() {
        assertEquals(1, Chapter(0, "Only", 0, 10).pagesLeft(12))
    }
}

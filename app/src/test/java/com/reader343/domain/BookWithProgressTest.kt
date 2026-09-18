package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookWithProgressTest {

    private fun book(
        id: Long,
        percent: Float = 0f,
        lastReadAt: Long? = null,
        finishedAt: Long? = null,
        notes: Int = 0,
    ) = BookWithProgress(
        id = id,
        title = "Book $id",
        coverPath = null,
        pageCount = 100,
        lastPage = (percent * 100).toInt(),
        percent = percent,
        lastReadAt = lastReadAt,
        finishedAt = finishedAt,
        noteCount = notes,
    )

    private val notStarted = book(1)
    private val reading = book(2, percent = 0.3f, lastReadAt = 10L, notes = 2)
    private val almostDone = book(3, percent = 0.75f, lastReadAt = 20L)
    private val finished = book(4, percent = 1f, lastReadAt = 30L, finishedAt = 30L)
    private val books = listOf(notStarted, reading, almostDone, finished)

    @Test
    fun statusFollowsProgress() {
        assertEquals(BookStatus.NotStarted, notStarted.status)
        assertEquals(BookStatus.Reading, reading.status)
        assertEquals(BookStatus.AlmostDone, almostDone.status)
        assertEquals(BookStatus.Finished, finished.status)
    }

    @Test
    fun filtersSelectMatchingBooks() {
        assertEquals(listOf(reading, almostDone), books.filteredBy(LibraryFilter.Reading))
        assertEquals(books, books.filteredBy(LibraryFilter.All))
        assertEquals(listOf(finished), books.filteredBy(LibraryFilter.Finished))
        assertEquals(listOf(reading), books.filteredBy(LibraryFilter.WithNotes))
    }

    @Test
    fun continueCandidateSkipsFinishedAndUnstarted() {
        assertEquals(almostDone, books.continueCandidate())
        assertNull(listOf(notStarted, finished).continueCandidate())
    }
}

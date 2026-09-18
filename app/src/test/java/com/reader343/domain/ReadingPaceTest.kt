package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingPaceTest {

    @Test
    fun paceNeedsEnoughPages() {
        assertNull(msPerPage(timeMs = 600_000L, pages = 2))
        assertNull(msPerPage(timeMs = 0L, pages = 10))
        assertEquals(60_000L, msPerPage(timeMs = 600_000L, pages = 10))
    }

    @Test
    fun bookPacePreferredOverOverall() {
        assertEquals(30_000L, ReadingPace(bookMsPerPage = 30_000L, overallMsPerPage = 90_000L).msPerPage)
        assertEquals(90_000L, ReadingPace(bookMsPerPage = null, overallMsPerPage = 90_000L).msPerPage)
        assertNull(ReadingPace.Unknown.msPerPage)
    }

    @Test
    fun timeForScalesWithPages() {
        val pace = ReadingPace(bookMsPerPage = 60_000L, overallMsPerPage = null)
        assertEquals(1_200_000L, pace.timeFor(20))
        assertEquals(0L, pace.timeFor(-3))
        assertNull(ReadingPace.Unknown.timeFor(20))
    }
}

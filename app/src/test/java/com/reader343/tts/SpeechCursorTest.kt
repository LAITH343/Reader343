package com.reader343.tts

import com.reader343.domain.SpeechPosition
import com.reader343.domain.SpeechSource
import com.reader343.domain.SpeechUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpeechCursorTest {

    private class FakeSource(private val pages: List<Int>) : SpeechSource {
        val loads = mutableListOf<Int>()
        override val bookId = 1L
        override val pageCount = pages.size
        override val locale: Locale = Locale.ENGLISH
        override suspend fun units(page: Int): List<SpeechUnit> {
            loads += page
            return (0 until pages[page]).map { SpeechUnit(page, it, 0, 0, "p$page s$it", emptyList()) }
        }
    }

    private fun ids(units: List<SpeechUnit>?) = units?.map { it.id }

    @Test
    fun startsFromChosenSentence() = runBlocking {
        val cursor = SpeechCursor(FakeSource(listOf(3, 2)))
        assertEquals(listOf("0:1", "0:2"), ids(cursor.from(SpeechPosition(0, 1))))
    }

    @Test
    fun startPastPageEndMovesToNextPageWithText() = runBlocking {
        val cursor = SpeechCursor(FakeSource(listOf(2, 0, 0, 2)))
        assertEquals(listOf("3:0", "3:1"), ids(cursor.from(SpeechPosition(0, 5))))
        assertEquals(listOf("3:0", "3:1"), ids(cursor.from(SpeechPosition(1, 0))))
    }

    @Test
    fun startOutsideBookIsEmpty() = runBlocking {
        val cursor = SpeechCursor(FakeSource(listOf(2)))
        assertEquals(emptyList<String>(), ids(cursor.from(SpeechPosition(4, 0))))
    }

    @Test
    fun afterSkipsEmptyPagesAndStopsAtBookEnd() = runBlocking {
        val cursor = SpeechCursor(FakeSource(listOf(1, 0, 2, 0)))
        assertEquals(listOf("2:0", "2:1"), ids(cursor.after(0)))
        assertNull(cursor.after(2))
    }

    @Test
    fun nextAndPreviousCrossPageBoundaries() = runBlocking {
        val cursor = SpeechCursor(FakeSource(listOf(2, 0, 2)))
        assertEquals("0:1", cursor.next(SpeechPosition(0, 0))?.id)
        assertEquals("2:0", cursor.next(SpeechPosition(0, 1))?.id)
        assertNull(cursor.next(SpeechPosition(2, 1)))
        assertEquals("0:1", cursor.previous(SpeechPosition(2, 0))?.id)
        assertEquals("2:0", cursor.previous(SpeechPosition(2, 1))?.id)
        assertNull(cursor.previous(SpeechPosition(0, 0)))
    }

    @Test
    fun cachesRecentPages() = runBlocking {
        val source = FakeSource(listOf(2, 2))
        val cursor = SpeechCursor(source)
        cursor.from(SpeechPosition(0, 0))
        cursor.next(SpeechPosition(0, 0))
        cursor.next(SpeechPosition(0, 1))
        cursor.previous(SpeechPosition(1, 0))
        assertEquals(listOf(0, 1), source.loads)
    }
}

package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAvailabilityTest {

    @Test
    fun proseIsUsable() {
        assertTrue(isUsableText("It was a bright cold day in April, and the clocks were striking thirteen."))
        assertTrue(isUsableText("كان يوماً مشرقاً بارداً من أيام نيسان والساعات تدق"))
    }

    @Test
    fun emptyOrJunkIsNotUsable() {
        assertFalse(isUsableText(""))
        assertFalse(isUsableText("   \n\t  "))
        assertFalse(isUsableText("12"))
        assertFalse(isUsableText("￾￾ . , ; - \u0000  ----- ....."))
    }

    @Test
    fun thresholdCountsLettersAndDigitsOnly() {
        assertFalse(isUsableText("a b c d e f g h i j k l m n o p q r s"))
        assertTrue(isUsableText("a b c d e f g h i j k l m n o p q r s t"))
    }

    @Test
    fun samplesAllPagesOfShortBooks() {
        assertEquals(emptyList<Int>(), textSamplePages(0))
        assertEquals(listOf(0), textSamplePages(1))
        assertEquals(listOf(0, 1, 2, 3, 4), textSamplePages(5))
    }

    @Test
    fun samplesSpreadAcrossLongBooksAvoidingEnds() {
        val pages = textSamplePages(600)
        assertEquals(listOf(100, 200, 300, 400, 500), pages)
        assertEquals(5, textSamplePages(7).size)
        assertTrue(textSamplePages(7).all { it in 1..5 })
    }

    @Test
    fun textBookDetected() {
        assertTrue(detectTextLayer(300) { "Chapter text with plenty of words on page $it" })
    }

    @Test
    fun scannedBookNotDetected() {
        assertFalse(detectTextLayer(300) { "" })
        assertFalse(detectTextLayer(300) { null })
    }

    @Test
    fun imagePagesInsideTextBookStillDetected() {
        assertTrue(detectTextLayer(300) { page -> if (page == 200) "Body text on a normal printed page" else "" })
    }
}

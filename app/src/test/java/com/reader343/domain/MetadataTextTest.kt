package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTextTest {

    @Test
    fun isbn13ChecksumValidates() {
        assertTrue(isValidIsbn13("9780990582939"))
        assertFalse(isValidIsbn13("9780990582938"))
        assertFalse(isValidIsbn13("1234567890128"))
    }

    @Test
    fun isbn10ChecksumValidatesIncludingX() {
        assertTrue(isValidIsbn10("0990582930"))
        assertTrue(isValidIsbn10("080442957X"))
        assertFalse(isValidIsbn10("0990582931"))
    }

    @Test
    fun isbn10ConvertsTo13() {
        assertEquals("9780990582939", isbn10To13("0990582930"))
    }

    @Test
    fun findsHyphenatedLabeledIsbn13() {
        val text = "Copyright 2021 Robert Nystrom\nISBN 978-0-9905829-3-9 (paperback)\nPrinted in USA"
        assertEquals("9780990582939", findIsbn(text))
    }

    @Test
    fun findsLabeledIsbn10AndConvertsIt() {
        assertEquals("9780990582939", findIsbn("ISBN-10: 0-9905829-3-0"))
    }

    @Test
    fun ignoresUnlabeledIsbn10LookalikePhoneNumbers() {
        assertNull(findIsbn("Call 0990582930 for orders"))
    }

    @Test
    fun acceptsUnlabeledIsbn13() {
        assertEquals("9780990582939", findIsbn("printed copy 9780990582939."))
    }

    @Test
    fun prefersLabeledOverUnlabeled() {
        val text = "Also by the author: 9780990582939\nISBN: 978-1-59327-828-1"
        assertEquals("9781593278281", findIsbn(text))
    }

    @Test
    fun keepsIsbnWhenFollowedByAnotherNumber() {
        assertEquals("9780990582939", findIsbn("ISBN 978-0-9905829-3-9 12"))
    }

    @Test
    fun rejectsInvalidChecksum() {
        assertNull(findIsbn("ISBN 978-0-9905829-3-8"))
    }

    @Test
    fun cleansHyphenatedFilename() {
        assertEquals("crafting interpreters", cleanTitleQuery("crafting-interpreters"))
    }

    @Test
    fun cleansUnderscoresBracketsAndNoise() {
        assertEquals(
            "Designing Data Intensive Applications",
            cleanTitleQuery("Designing_Data_Intensive_Applications (O'Reilly) [2017] z-lib.org.pdf"),
        )
    }

    @Test
    fun treatsHyphensAsSeparatorsWhenTheyDominate() {
        assertEquals(
            "Martin Kleppmann Designing Data Intensive Applications O'Reilly Media",
            cleanTitleQuery("Martin-Kleppmann---Designing-Data-Intensive-Applications_-O'Reilly-Media-(2017)"),
        )
    }

    @Test
    fun keepsHyphenInsideWordsWhenSpacesExist() {
        assertEquals("Designing Data-Intensive Applications", cleanTitleQuery("Designing Data-Intensive Applications 2nd edition"))
    }

    @Test
    fun dropsAuthorSeparatorDash() {
        assertEquals("Kleppmann Designing Data", cleanTitleQuery("Kleppmann - Designing Data"))
    }

    @Test
    fun keepsArabicTitles() {
        assertEquals("مقدمة ابن خلدون", cleanTitleQuery("مقدمة_ابن_خلدون.pdf"))
    }

    @Test
    fun similarityIsOneForSameTokens() {
        assertEquals(1f, titleSimilarity("crafting interpreters", "Crafting Interpreters"), 0.001f)
    }

    @Test
    fun similarityDropsWithExtraWords() {
        val exact = titleSimilarity("crafting interpreters", "Crafting Interpreters")
        val draft = titleSimilarity("crafting interpreters", "Crafting Interpreters (draft edition)")
        val other = titleSimilarity("crafting interpreters", "The Craft of Interpretation")
        assertTrue(exact > draft)
        assertTrue(draft > other)
    }

    @Test
    fun rankingSortsByScoreAndMarksClosest() {
        val query = MetadataQuery.Title("crafting interpreters")
        val ranked = rankCandidates(
            query,
            listOf(
                meta("The Craft of Interpretation"),
                meta("Crafting Interpreters"),
                meta("Crafting Interpreters (draft edition)"),
            ),
        )
        assertEquals("Crafting Interpreters", ranked[0].metadata.title)
        assertTrue(ranked[0].closest)
        assertFalse(ranked[1].closest)
        assertEquals("The Craft of Interpretation", ranked[2].metadata.title)
    }

    @Test
    fun rankingDoesNotMarkWeakTopMatch() {
        val ranked = rankCandidates(MetadataQuery.Title("operating systems"), listOf(meta("Cooking at Home")))
        assertFalse(ranked.single().closest)
    }

    @Test
    fun isbnRankingPrefersExactIsbn() {
        val ranked = rankCandidates(
            MetadataQuery.Isbn("9780990582939"),
            listOf(meta("Other", isbn = "9781593278281"), meta("Crafting Interpreters", isbn = "9780990582939")),
        )
        assertEquals("Crafting Interpreters", ranked.first().metadata.title)
    }

    @Test
    fun joinsTwoAuthorsWithAmpersand() {
        assertEquals("Steve Klabnik & Carol Nichols", joinAuthors(listOf("Steve Klabnik", "Carol Nichols")))
        assertEquals("A, B, C", joinAuthors(listOf("A", "B", "C")))
        assertNull(joinAuthors(listOf(" ")))
    }

    private fun meta(title: String, isbn: String? = null) =
        BookMetadata(title = title, isbn = isbn, provider = MetadataProvider.OpenLibrary)
}

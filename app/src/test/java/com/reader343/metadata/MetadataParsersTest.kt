package com.reader343.metadata

import com.reader343.domain.MetadataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataParsersTest {

    @Test
    fun parsesGoogleBooksVolume() {
        val json = """
            {"items": [{"volumeInfo": {
                "title": "Crafting Interpreters",
                "authors": ["Robert Nystrom"],
                "publisher": "Genever Benning",
                "publishedDate": "2021-07-27",
                "description": "<p>Two interpreters.</p><p>Built &amp; explained.</p>",
                "pageCount": 640,
                "industryIdentifiers": [
                    {"type": "ISBN_10", "identifier": "0990582930"},
                    {"type": "ISBN_13", "identifier": "9780990582939"}
                ],
                "imageLinks": {"thumbnail": "http://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api"}
            }}]}
        """.trimIndent()
        val result = GoogleBooksParser.parse(json).single()
        assertEquals("Crafting Interpreters", result.title)
        assertEquals("Robert Nystrom", result.author)
        assertEquals(2021, result.publishedYear)
        assertEquals("Genever Benning", result.publisher)
        assertEquals(640, result.pageCount)
        assertEquals("9780990582939", result.isbn)
        assertEquals("Two interpreters.\nBuilt & explained.", result.description)
        assertEquals(
            "https://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1&source=gbs_api",
            result.coverUrl,
        )
        assertEquals(MetadataProvider.GoogleBooks, result.provider)
    }

    @Test
    fun googleBooksWithoutItemsIsEmpty() {
        assertTrue(GoogleBooksParser.parse("""{"kind": "books#volumes", "totalItems": 0}""").isEmpty())
    }

    @Test
    fun googleBooksSkipsUntitledAndJoinsSubtitle() {
        val json = """
            {"items": [
                {"volumeInfo": {"authors": ["Nobody"]}},
                {"volumeInfo": {"title": "Rust", "subtitle": "The Book", "publishedDate": "2019", "publisher": "\"O'Reilly Media, Inc.\""}}
            ]}
        """.trimIndent()
        val result = GoogleBooksParser.parse(json).single()
        assertEquals("Rust: The Book", result.title)
        assertEquals(2019, result.publishedYear)
        assertEquals("O'Reilly Media, Inc.", result.publisher)
        assertNull(result.coverUrl)
        assertNull(result.isbn)
    }

    @Test
    fun parsesOpenLibrarySearchDocs() {
        val json = """
            {"numFound": 2, "docs": [
                {"key": "/works/OL26124818W", "title": "Crafting Interpreters", "author_name": ["Robert Nystrom"],
                 "first_publish_year": 2021, "isbn": ["0990582930", "9780990582939"], "cover_i": 12075000,
                 "publisher": ["Genever Benning"], "number_of_pages_median": 753},
                {"key": "/works/OL1W", "title": "Crafting Interpreters", "author_name": ["Maria Kelly"]}
            ]}
        """.trimIndent()
        val results = OpenLibraryParser.parse(json)
        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("Robert Nystrom", first.author)
        assertEquals(2021, first.publishedYear)
        assertEquals("9780990582939", first.isbn)
        assertEquals("https://covers.openlibrary.org/b/id/12075000-L.jpg", first.coverUrl)
        assertEquals("Genever Benning", first.publisher)
        assertEquals(753, first.pageCount)
        assertEquals("/works/OL26124818W", first.workKey)
        assertNull(results[1].coverUrl)
        assertEquals(MetadataProvider.OpenLibrary, first.provider)
    }

    @Test
    fun openLibraryPrefersQueriedIsbn() {
        val json = """{"docs": [{"title": "T", "isbn": ["9781111111111", "0990582930", "9780990582939"]}]}"""
        assertEquals("9780990582939", OpenLibraryParser.parse(json, preferredIsbn = "9780990582939").single().isbn)
    }

    @Test
    fun parsesOpenLibraryDescriptionString() {
        assertEquals("Plain text.", OpenLibraryParser.parseDescription("""{"description": "Plain text."}"""))
    }

    @Test
    fun parsesOpenLibraryDescriptionObjectAndStripsLinks() {
        val json = """{"description": {"type": "/type/text", "value": "See [the site](https://example.com) now.\n----------\nSource list"}}"""
        assertEquals("See the site now.", OpenLibraryParser.parseDescription(json))
    }

    @Test
    fun missingOpenLibraryDescriptionIsNull() {
        assertNull(OpenLibraryParser.parseDescription("""{"title": "x"}"""))
    }
}

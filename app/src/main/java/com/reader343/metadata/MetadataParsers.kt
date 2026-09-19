package com.reader343.metadata

import com.reader343.domain.BookMetadata
import com.reader343.domain.MetadataProvider
import org.json.JSONArray
import org.json.JSONObject

object GoogleBooksParser {

    fun parse(json: String): List<BookMetadata> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val info = items.optJSONObject(i)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = listOfNotNull(
                info.optStringOrNull("title"),
                info.optStringOrNull("subtitle"),
            ).joinToString(": ").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BookMetadata(
                title = title,
                authors = info.optJSONArray("authors").strings(),
                description = info.optStringOrNull("description")?.let(::stripHtml),
                publishedYear = info.optStringOrNull("publishedDate")?.let(::yearOf),
                publisher = info.optStringOrNull("publisher")?.trim('"', ' ')?.takeIf { it.isNotEmpty() },
                pageCount = info.optInt("pageCount", 0).takeIf { it > 0 },
                isbn = isbnOf(info.optJSONArray("industryIdentifiers")),
                coverUrl = info.optJSONObject("imageLinks")?.let(::coverOf),
                provider = MetadataProvider.GoogleBooks,
            )
        }
    }

    private fun isbnOf(ids: JSONArray?): String? {
        if (ids == null) return null
        val byType = (0 until ids.length()).mapNotNull { ids.optJSONObject(it) }
            .associate { it.optString("type") to it.optString("identifier") }
        return byType["ISBN_13"]?.takeIf { it.isNotBlank() } ?: byType["ISBN_10"]?.takeIf { it.isNotBlank() }
    }

    private fun coverOf(links: JSONObject): String? {
        val raw = links.optStringOrNull("thumbnail") ?: links.optStringOrNull("smallThumbnail") ?: return null
        return raw.replaceFirst("http://", "https://").replace("&edge=curl", "")
    }
}

object OpenLibraryParser {

    const val SEARCH_FIELDS = "key,title,subtitle,author_name,first_publish_year,isbn,cover_i,publisher,number_of_pages_median"

    fun parse(json: String, preferredIsbn: String? = null): List<BookMetadata> {
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val doc = docs.optJSONObject(i) ?: return@mapNotNull null
            val title = listOfNotNull(
                doc.optStringOrNull("title"),
                doc.optStringOrNull("subtitle"),
            ).joinToString(": ").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val isbns = doc.optJSONArray("isbn").strings().map { it.replace("-", "") }
            val coverId = doc.optLong("cover_i", 0L)
            BookMetadata(
                title = title,
                authors = doc.optJSONArray("author_name").strings(),
                publishedYear = doc.optInt("first_publish_year", 0).takeIf { it > 0 },
                publisher = doc.optJSONArray("publisher").strings().firstOrNull(),
                pageCount = doc.optInt("number_of_pages_median", 0).takeIf { it > 0 },
                isbn = preferredIsbn?.takeIf { it in isbns } ?: isbns.firstOrNull { it.length == 13 } ?: isbns.firstOrNull(),
                coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null,
                provider = MetadataProvider.OpenLibrary,
                workKey = doc.optStringOrNull("key")?.takeIf { it.startsWith("/works/") },
            )
        }
    }

    fun parseDescription(json: String): String? {
        val root = JSONObject(json)
        val raw = when (val value = root.opt("description")) {
            is String -> value
            is JSONObject -> value.optStringOrNull("value")
            else -> null
        }
        return raw?.let(::stripMarkup)?.takeIf { it.isNotBlank() }
    }

    private fun stripMarkup(value: String): String =
        value.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .substringBefore("\n----------")
            .trim()
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotEmpty() } }
}

private fun yearOf(date: String): Int? = Regex("""\d{4}""").find(date)?.value?.toIntOrNull()

internal fun stripHtml(value: String): String =
    value.replace(Regex("""<br\s*/?>|</p>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

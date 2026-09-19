package com.reader343.domain

enum class MetadataProvider(val key: String) {
    GoogleBooks("google_books"),
    OpenLibrary("open_library"),
    ;

    companion object {
        fun fromKey(key: String?): MetadataProvider? = entries.firstOrNull { it.key == key }
    }
}

enum class MetadataStatus(val key: String) {
    Applied("applied"),
    Review("review"),
    None("none"),
    ;

    companion object {
        fun fromKey(key: String?): MetadataStatus? = entries.firstOrNull { it.key == key }
    }
}

data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val publishedYear: Int? = null,
    val publisher: String? = null,
    val pageCount: Int? = null,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val provider: MetadataProvider,
    val workKey: String? = null,
) {
    val author: String? get() = joinAuthors(authors)
}

fun joinAuthors(authors: List<String>): String? {
    val names = authors.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return when (names.size) {
        0 -> null
        2 -> "${names[0]} & ${names[1]}"
        else -> names.joinToString(", ")
    }
}

sealed interface MetadataQuery {
    val text: String

    data class Isbn(val isbn: String) : MetadataQuery {
        override val text: String get() = "isbn:$isbn"
    }

    data class Title(val title: String) : MetadataQuery {
        override val text: String get() = title
    }
}

sealed interface LookupResult {
    data class Found(val candidates: List<BookMetadata>) : LookupResult
    data object NoMatch : LookupResult
    data object Offline : LookupResult
    data object Failed : LookupResult
}

data class RankedCandidate(val metadata: BookMetadata, val score: Float, val closest: Boolean)

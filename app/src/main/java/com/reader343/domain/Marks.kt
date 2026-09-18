package com.reader343.domain

data class Mark(
    val page: Int,
    val highlight: Highlight?,
    val note: Note?,
) {
    val key: String get() = highlight?.let { "h${it.id}" } ?: "n${note?.id}"
    val quote: String? get() = highlight?.snippet ?: note?.anchor?.snippet
    val color: Int? get() = highlight?.color
    val updatedAt: Long get() = maxOf(highlight?.createdAt ?: 0L, note?.createdAt ?: 0L)
}

enum class MarkFilter { All, Highlights, Notes }

fun buildMarks(highlights: List<Highlight>, notes: List<Note>): List<Mark> {
    val highlightIds = highlights.mapTo(HashSet()) { it.id }
    val attached = notes
        .filter { it.anchor.highlightId in highlightIds }
        .groupBy { it.anchor.highlightId }
    val fromHighlights = highlights.map { highlight ->
        Mark(
            page = highlight.page,
            highlight = highlight,
            note = attached[highlight.id]?.maxByOrNull { it.createdAt },
        )
    }
    val loose = notes
        .filter { it.anchor.highlightId !in highlightIds }
        .map { Mark(page = it.page, highlight = null, note = it) }
    return (fromHighlights + loose).sortedWith(
        compareByDescending<Mark> { it.updatedAt }.thenByDescending { it.page },
    )
}

fun List<Mark>.filteredBy(filter: MarkFilter): List<Mark> = when (filter) {
    MarkFilter.All -> this
    MarkFilter.Highlights -> filter { it.highlight != null }
    MarkFilter.Notes -> filter { it.note != null }
}

package com.reader343.domain

data class OutlineEntry(
    val title: String,
    val page: Int,
    val depth: Int,
)

data class Chapter(
    val index: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
) {
    fun pagesLeft(page: Int): Int = (endPage - page).coerceAtLeast(1)
}

fun List<OutlineEntry>.chapterAt(page: Int, pageCount: Int): Chapter? {
    var current = -1
    forEachIndexed { index, entry ->
        if (entry.page <= page && (current < 0 || entry.page >= this[current].page)) current = index
    }
    if (current < 0) return null
    val entry = this[current]
    val end = minOfOrNull { if (it.page > page) it.page else Int.MAX_VALUE }
        ?.takeIf { it != Int.MAX_VALUE }
        ?: pageCount
    return Chapter(index = current, title = entry.title, startPage = entry.page, endPage = end)
}

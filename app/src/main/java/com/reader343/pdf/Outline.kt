package com.reader343.pdf

import com.reader343.domain.OutlineEntry
import io.legere.pdfiumandroid.api.Bookmark

internal fun List<Bookmark>.flatten(pageCount: Int): List<OutlineEntry> {
    val entries = mutableListOf<OutlineEntry>()
    fun visit(items: List<Bookmark>, depth: Int) {
        if (depth > MAX_DEPTH) return
        for (item in items) {
            if (entries.size >= MAX_ENTRIES) return
            val title = item.title?.trim().orEmpty()
            val page = item.pageIdx
            if (title.isNotEmpty() && page in 0 until pageCount) {
                entries += OutlineEntry(title = title, page = page.toInt(), depth = depth)
            }
            visit(item.children, depth + 1)
        }
    }
    visit(this, 0)
    return entries
}

private const val MAX_DEPTH = 12
private const val MAX_ENTRIES = 5_000

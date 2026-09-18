package com.reader343.domain

fun msPerPage(timeMs: Long, pages: Int): Long? =
    if (pages >= MIN_PACE_PAGES && timeMs > 0L) timeMs / pages else null

data class ReadingPace(
    val bookMsPerPage: Long?,
    val overallMsPerPage: Long?,
) {
    val msPerPage: Long? get() = bookMsPerPage ?: overallMsPerPage

    fun timeFor(pages: Int): Long? = msPerPage?.let { it * pages.coerceAtLeast(0) }

    companion object {
        val Unknown = ReadingPace(null, null)
    }
}

private const val MIN_PACE_PAGES = 3

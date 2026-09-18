package com.reader343.domain

data class BookWithProgress(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val pageCount: Int,
    val lastPage: Int,
    val percent: Float,
    val lastReadAt: Long?,
    val finishedAt: Long? = null,
    val highlightCount: Int = 0,
    val noteCount: Int = 0,
    val bookmarkCount: Int = 0,
    val chapterTitle: String? = null,
    val chapterEndPage: Int? = null,
    val msPerPage: Long? = null,
) {
    val finished: Boolean get() = finishedAt != null
    val started: Boolean get() = lastReadAt != null
    val inProgress: Boolean get() = started && !finished
    val marks: Int get() = highlightCount + noteCount + bookmarkCount
    val chapterTimeLeftMs: Long?
        get() {
            if (chapterTitle == null) return null
            val pace = msPerPage ?: return null
            val end = chapterEndPage ?: pageCount
            return (end - lastPage).coerceAtLeast(1) * pace
        }
    val status: BookStatus
        get() = when {
            finished -> BookStatus.Finished
            !started -> BookStatus.NotStarted
            percent >= ALMOST_DONE -> BookStatus.AlmostDone
            else -> BookStatus.Reading
        }

    private companion object {
        const val ALMOST_DONE = 0.75f
    }
}

enum class BookStatus { Reading, AlmostDone, NotStarted, Finished }

enum class LibraryFilter { Reading, All, Finished, WithNotes }

fun List<BookWithProgress>.continueCandidate(): BookWithProgress? =
    filter { it.inProgress }
        .maxByOrNull { it.lastReadAt ?: 0L }

fun List<BookWithProgress>.filteredBy(filter: LibraryFilter): List<BookWithProgress> = when (filter) {
    LibraryFilter.Reading -> filter { it.inProgress }
    LibraryFilter.All -> this
    LibraryFilter.Finished -> filter { it.finished }
    LibraryFilter.WithNotes -> filter { it.noteCount > 0 }
}

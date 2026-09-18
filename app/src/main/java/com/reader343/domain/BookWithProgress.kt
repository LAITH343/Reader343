package com.reader343.domain

data class BookWithProgress(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val pageCount: Int,
    val lastPage: Int,
    val percent: Float,
    val lastReadAt: Long?,
)

fun List<BookWithProgress>.continueCandidate(): BookWithProgress? =
    filter { it.lastReadAt != null && it.percent < 1f }
        .maxByOrNull { it.lastReadAt ?: 0L }

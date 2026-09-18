package com.reader343.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val progress: ProgressEntity?,
    val highlightCount: Int,
    val noteCount: Int,
    val bookmarkCount: Int,
    val chapterTitle: String?,
    val chapterEndPage: Int?,
    val readMs: Long,
    val readPages: Int,
)

data class PaceRow(
    val timeMs: Long,
    val pages: Int,
)

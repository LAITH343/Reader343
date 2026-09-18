package com.reader343.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId")
    val progress: ProgressEntity?,
    val highlightCount: Int,
    val noteCount: Int,
)

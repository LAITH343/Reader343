package com.reader343.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUri: String,
    val filePath: String,
    val pageCount: Int,
    val coverPath: String?,
    val addedAt: Long,
)

@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ProgressEntity(
    @PrimaryKey val bookId: Long,
    val lastPage: Int,
    val scrollOffset: Float,
    val percent: Float,
    val updatedAt: Long,
    val finishedAt: Long? = null,
)

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val rectsJson: String,
    val color: Int,
    val charStart: Int?,
    val charEnd: Int?,
    val snippet: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val page: Int,
    val anchorJson: String,
    val body: String,
    val createdAt: Long,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val startTs: Long,
    val endTs: Long?,
    val pagesRead: Int,
)

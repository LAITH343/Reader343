package com.reader343.domain

data class NoteAnchor(
    val rect: NormRect,
    val highlightId: Long?,
    val snippet: String?,
)

data class Note(
    val id: Long,
    val page: Int,
    val anchor: NoteAnchor,
    val body: String,
    val createdAt: Long,
)

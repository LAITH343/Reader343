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

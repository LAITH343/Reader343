package com.reader343.domain

data class Book(
    val id: Long,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val lastPage: Int,
    val hasTextLayer: Boolean?,
)

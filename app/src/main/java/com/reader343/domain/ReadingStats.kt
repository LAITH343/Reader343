package com.reader343.domain

import java.time.LocalDate

data class ReadingStats(
    val streakDays: Int,
    val totalTimeMs: Long,
    val booksInProgress: Int,
    val sessionCount: Int,
    val avgSessionMs: Long,
    val pagesPerDay: Float,
    val days: List<DayStats>,
    val books: List<BookStats>,
)

data class DayStats(
    val date: LocalDate,
    val timeMs: Long,
    val pages: Int,
)

data class BookStats(
    val bookId: Long,
    val title: String,
    val totalTimeMs: Long,
    val sessionCount: Int,
    val avgSessionMs: Long,
    val pagesRead: Int,
    val percent: Float,
)

enum class ActivityMetric { Minutes, Pages }

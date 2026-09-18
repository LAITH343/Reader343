package com.reader343.data.repo

import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.SessionEntity
import com.reader343.domain.ActivityMetric
import com.reader343.domain.BookStats
import com.reader343.domain.DayStats
import com.reader343.domain.ReadingStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val bookDao: BookDao,
    private val sessionDao: SessionDao,
) {

    fun observeStats(): Flow<ReadingStats> =
        combine(bookDao.observeWithProgress(), sessionDao.observeFinished()) { books, sessions ->
            compute(books, sessions, LocalDate.now(), ZoneId.systemDefault())
        }.flowOn(Dispatchers.Default)

    fun observeDailyActivity(metric: ActivityMetric): Flow<Map<LocalDate, Int>> =
        sessionDao.observeFinished()
            .map { sessions -> dailyActivity(sessions, metric, ZoneId.systemDefault()) }
            .flowOn(Dispatchers.Default)

    private fun dailyActivity(
        sessions: List<SessionEntity>,
        metric: ActivityMetric,
        zone: ZoneId,
    ): Map<LocalDate, Int> =
        sessions.groupBy { it.startTs.toLocalDate(zone) }
            .mapValues { (_, daySessions) ->
                when (metric) {
                    ActivityMetric.Minutes -> {
                        val ms = daySessions.sumOf { it.durationMs }
                        if (ms > 0L) (ms / MINUTE_MS).toInt().coerceAtLeast(1) else 0
                    }
                    ActivityMetric.Pages -> daySessions.sumOf { it.pagesRead }
                }
            }
            .filterValues { it > 0 }

    private fun compute(
        books: List<BookWithProgressRow>,
        sessions: List<SessionEntity>,
        today: LocalDate,
        zone: ZoneId,
    ): ReadingStats {
        val byBook = sessions.groupBy { it.bookId }
        val byDay = sessions.groupBy { it.startTs.toLocalDate(zone) }
        val totalTime = sessions.sumOf { it.durationMs }

        val bookStats = books.mapNotNull { row ->
            val bookSessions = byBook[row.book.id].orEmpty()
            val percent = row.progress?.percent ?: 0f
            val started = (row.progress?.updatedAt ?: 0L) > 0L
            if (bookSessions.isEmpty() && !started) return@mapNotNull null
            val time = bookSessions.sumOf { it.durationMs }
            BookStats(
                bookId = row.book.id,
                title = row.book.title,
                totalTimeMs = time,
                sessionCount = bookSessions.size,
                avgSessionMs = if (bookSessions.isEmpty()) 0L else time / bookSessions.size,
                pagesRead = bookSessions.sumOf { it.pagesRead },
                percent = percent,
            )
        }

        val days = (CHART_DAYS - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val daySessions = byDay[date].orEmpty()
            DayStats(
                date = date,
                timeMs = daySessions.sumOf { it.durationMs },
                pages = daySessions.sumOf { it.pagesRead },
            )
        }

        return ReadingStats(
            streakDays = streak(byDay.keys, today),
            totalTimeMs = totalTime,
            booksInProgress = bookStats.count { it.percent < 1f },
            sessionCount = sessions.size,
            avgSessionMs = if (sessions.isEmpty()) 0L else totalTime / sessions.size,
            pagesPerDay = if (byDay.isEmpty()) 0f else sessions.sumOf { it.pagesRead }.toFloat() / byDay.size,
            days = days,
            books = bookStats,
        )
    }

    private fun streak(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = when {
            today in activeDays -> today
            today.minusDays(1) in activeDays -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (day in activeDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    private val SessionEntity.durationMs: Long
        get() = ((endTs ?: startTs) - startTs).coerceAtLeast(0L)

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private companion object {
        const val CHART_DAYS = 14
        const val MINUTE_MS = 60_000L
    }
}

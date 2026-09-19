package com.reader343.data.repo

import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.SessionEntity
import com.reader343.domain.ActivityMetric
import com.reader343.domain.BookStats
import com.reader343.domain.DailyGoal
import com.reader343.domain.DayStats
import com.reader343.domain.GoalChange
import com.reader343.domain.GoalContext
import com.reader343.domain.ReadingSession
import com.reader343.domain.ReadingStats
import com.reader343.domain.StreakSnapshot
import com.reader343.domain.bestStreakDays
import com.reader343.domain.currentStreak
import com.reader343.domain.dailyStats
import com.reader343.domain.goalContext
import com.reader343.domain.goalHitRate
import com.reader343.domain.goalMetDays
import com.reader343.domain.streakBreaks
import com.reader343.domain.strongestSlot
import com.reader343.domain.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val bookDao: BookDao,
    private val sessionDao: SessionDao,
    private val settingsRepository: SettingsRepository,
) {

    fun observeStats(): Flow<ReadingStats> =
        combine(
            bookDao.observeWithProgress(),
            sessionDao.observeFinished(),
            settingsRepository.settings.map { it.goal to it.goalHistory }.distinctUntilChanged(),
        ) { books, sessions, (goal, history) ->
            val zone = ZoneId.systemDefault()
            compute(books, sessions.map { it.toDomain() }, goal, history, LocalDate.now(zone), zone)
        }.flowOn(Dispatchers.Default)

    fun observeGoalContext(): Flow<GoalContext> =
        combine(
            sessionDao.observeFinished(),
            settingsRepository.settings.map { it.goal }.distinctUntilChanged(),
        ) { sessions, goal ->
            val zone = ZoneId.systemDefault()
            goalContext(sessions.map { it.toDomain() }, goal, LocalDate.now(zone), zone)
        }.flowOn(Dispatchers.Default)

    fun observeStreakDays(): Flow<Int> =
        combine(sessionDao.observeFinished(), settingsRepository.settings) { sessions, settings ->
            val zone = ZoneId.systemDefault()
            val metDays = goalMetDays(dailyStats(sessions.map { it.toDomain() }, zone), settings.goalHistory)
            streakDays(metDays, settings.goal, settings.goalHistory, LocalDate.now(zone))
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    fun observeDailyActivity(metric: ActivityMetric): Flow<Map<LocalDate, Int>> =
        sessionDao.observeFinished()
            .map { sessions -> dailyActivity(sessions.map { it.toDomain() }, metric, ZoneId.systemDefault()) }
            .flowOn(Dispatchers.Default)

    suspend fun streakSnapshot(): StreakSnapshot {
        val sessions = sessionDao.finished()
        val settings = settingsRepository.settings.first()
        return withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val metDays = goalMetDays(dailyStats(sessions.map { it.toDomain() }, zone), settings.goalHistory)
            StreakSnapshot(
                days = streakDays(metDays, settings.goal, settings.goalHistory, today),
                goalMetToday = today in metDays,
            )
        }
    }

    private fun streakDays(metDays: Set<LocalDate>, goal: DailyGoal, history: List<GoalChange>, today: LocalDate): Int =
        if (goal.enabled) currentStreak(metDays, today, history.streakBreaks()) else 0

    private fun dailyActivity(
        sessions: List<ReadingSession>,
        metric: ActivityMetric,
        zone: ZoneId,
    ): Map<LocalDate, Int> =
        dailyStats(sessions, zone)
            .mapValues { (_, day) ->
                when (metric) {
                    ActivityMetric.Minutes ->
                        if (day.timeMs > 0L) (day.timeMs / MINUTE_MS).toInt().coerceAtLeast(1) else 0
                    ActivityMetric.Pages -> day.pages
                }
            }
            .filterValues { it > 0 }

    private fun compute(
        books: List<BookWithProgressRow>,
        sessions: List<ReadingSession>,
        goal: DailyGoal,
        history: List<GoalChange>,
        today: LocalDate,
        zone: ZoneId,
    ): ReadingStats {
        val byBook = sessions.groupBy { it.bookId }
        val byDay = dailyStats(sessions, zone)
        val windowStart = today.minusDays(WINDOW_DAYS - 1)
        val windowSessions = sessions.filter { !it.startTs.toLocalDate(zone).isBefore(windowStart) }
        val windowDays = byDay.values.filter { !it.date.isBefore(windowStart) }
        val windowTime = windowSessions.sumOf { it.durationMs }
        val metDays = goalMetDays(byDay, history)

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
                pagesRead = bookSessions.sumOf { it.pages },
                percent = percent,
            )
        }

        val days = (CHART_DAYS - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset)
            byDay[date] ?: DayStats(date = date, timeMs = 0L, pages = 0)
        }

        return ReadingStats(
            streakDays = streakDays(metDays, goal, history, today),
            bestStreakDays = bestStreakDays(metDays, history.streakBreaks()),
            goalSet = goal.enabled,
            goalMetToday = today in metDays,
            totalTimeMs = windowTime,
            booksInProgress = books.count { row ->
                row.progress?.let { it.updatedAt > 0L && it.finishedAt == null } == true
            },
            sessionCount = windowSessions.size,
            avgSessionMs = if (windowSessions.isEmpty()) 0L else windowTime / windowSessions.size,
            pagesPerDay = if (windowDays.isEmpty()) 0f else windowDays.sumOf { it.pages }.toFloat() / windowDays.size,
            goalHitRate = goalHitRate(byDay, goal, today, windowStart),
            strongestSlot = strongestSlot(sessions, today, zone),
            days = days,
            books = bookStats,
        )
    }

    private fun SessionEntity.toDomain() = ReadingSession(
        bookId = bookId,
        startTs = startTs,
        endTs = endTs ?: startTs,
        pages = pagesRead,
    )

    private companion object {
        const val CHART_DAYS = 14L
        const val WINDOW_DAYS = 140L
        const val MINUTE_MS = 60_000L
    }
}

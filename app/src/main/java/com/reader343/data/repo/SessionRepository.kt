package com.reader343.data.repo

import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.SessionEntity
import com.reader343.domain.ReadingPace
import com.reader343.domain.msPerPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class SessionStart(val startTs: Long, val basePages: Int)

enum class SessionHolder { Reader, Listening }

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {

    private val mutex = Mutex()
    private val active = mutableMapOf<Long, ActiveSession>()

    suspend fun open(bookId: Long, now: Long, holder: SessionHolder = SessionHolder.Reader): SessionStart = mutex.withLock {
        active[bookId]?.let { session ->
            session.holders.getOrPut(holder) { 0 }
            return@withLock session.toStart()
        }
        sessionDao.deleteUnfinished(bookId)
        val last = sessionDao.latestFinished(bookId)
        val lastEnd = last?.endTs
        val session = if (last != null && lastEnd != null && now - lastEnd in 0..RESUME_GAP_MS) {
            ActiveSession(last.id, last.startTs, last.pagesRead)
        } else {
            val id = sessionDao.insert(SessionEntity(bookId = bookId, startTs = now, endTs = null, pagesRead = 0))
            ActiveSession(id, now, 0)
        }
        session.holders[holder] = 0
        active[bookId] = session
        session.toStart()
    }

    suspend fun pace(bookId: Long): ReadingPace {
        val book = sessionDao.paceForBook(bookId)
        val overall = sessionDao.paceOverall()
        return ReadingPace(
            bookMsPerPage = msPerPage(book.timeMs, book.pages),
            overallMsPerPage = msPerPage(overall.timeMs, overall.pages),
        )
    }

    suspend fun checkpoint(
        bookId: Long,
        now: Long,
        pagesRead: Int,
        holder: SessionHolder = SessionHolder.Reader,
    ) = mutex.withLock {
        val session = active[bookId] ?: return@withLock
        if (holder !in session.holders) return@withLock
        session.holders[holder] = pagesRead
        if (now - session.startTs >= MIN_DURATION_MS) {
            sessionDao.finish(session.id, now, session.pages)
        }
    }

    suspend fun close(
        bookId: Long,
        now: Long,
        pagesRead: Int,
        holder: SessionHolder = SessionHolder.Reader,
    ) = mutex.withLock {
        val session = active[bookId] ?: return@withLock
        if (holder !in session.holders) return@withLock
        session.holders.remove(holder)
        session.basePages += pagesRead
        when {
            session.holders.isNotEmpty() -> if (now - session.startTs >= MIN_DURATION_MS) {
                sessionDao.finish(session.id, now, session.pages)
            }
            now - session.startTs < MIN_DURATION_MS -> {
                active.remove(bookId)
                sessionDao.deleteById(session.id)
            }
            else -> {
                active.remove(bookId)
                sessionDao.finish(session.id, now, session.pages)
            }
        }
    }

    private class ActiveSession(val id: Long, val startTs: Long, var basePages: Int) {
        val holders = mutableMapOf<SessionHolder, Int>()

        val pages: Int get() = basePages + holders.values.sum()

        fun toStart() = SessionStart(startTs = startTs, basePages = pages)
    }

    private companion object {
        const val MIN_DURATION_MS = 5_000L
        const val RESUME_GAP_MS = 5_000L
    }
}

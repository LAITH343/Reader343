package com.reader343.data.repo

import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.SessionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {

    private val mutex = Mutex()
    private val active = mutableMapOf<Long, ActiveSession>()

    suspend fun open(bookId: Long, now: Long) = mutex.withLock {
        if (bookId in active) return@withLock
        sessionDao.deleteUnfinished(bookId)
        val last = sessionDao.latestFinished(bookId)
        val lastEnd = last?.endTs
        active[bookId] = if (last != null && lastEnd != null && now - lastEnd in 0..RESUME_GAP_MS) {
            ActiveSession(last.id, last.startTs, last.pagesRead)
        } else {
            val id = sessionDao.insert(SessionEntity(bookId = bookId, startTs = now, endTs = null, pagesRead = 0))
            ActiveSession(id, now, 0)
        }
    }

    suspend fun checkpoint(bookId: Long, now: Long, pagesRead: Int) = mutex.withLock {
        val session = active[bookId] ?: return@withLock
        if (now - session.startTs >= MIN_DURATION_MS) {
            sessionDao.finish(session.id, now, session.basePages + pagesRead)
        }
    }

    suspend fun close(bookId: Long, now: Long, pagesRead: Int) = mutex.withLock {
        val session = active.remove(bookId) ?: return@withLock
        if (now - session.startTs < MIN_DURATION_MS) {
            sessionDao.deleteById(session.id)
        } else {
            sessionDao.finish(session.id, now, session.basePages + pagesRead)
        }
    }

    private data class ActiveSession(val id: Long, val startTs: Long, val basePages: Int)

    private companion object {
        const val MIN_DURATION_MS = 5_000L
        const val RESUME_GAP_MS = 5_000L
    }
}

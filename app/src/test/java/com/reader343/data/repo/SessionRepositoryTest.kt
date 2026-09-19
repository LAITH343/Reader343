package com.reader343.data.repo

import com.reader343.data.db.dao.SessionDao
import com.reader343.data.db.entity.PaceRow
import com.reader343.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRepositoryTest {

    private class FakeSessionDao : SessionDao {
        val rows = mutableMapOf<Long, SessionEntity>()
        private var nextId = 1L

        override suspend fun insert(session: SessionEntity): Long {
            val id = nextId++
            rows[id] = session.copy(id = id)
            return id
        }

        override suspend fun latestFinished(bookId: Long): SessionEntity? =
            rows.values.filter { it.bookId == bookId && it.endTs != null }.maxByOrNull { it.endTs!! }

        override suspend fun finish(id: Long, endTs: Long, pagesRead: Int) {
            rows[id] = rows.getValue(id).copy(endTs = endTs, pagesRead = pagesRead)
        }

        override suspend fun deleteById(id: Long) {
            rows.remove(id)
        }

        override suspend fun deleteUnfinished(bookId: Long) {
            rows.values.removeAll { it.bookId == bookId && it.endTs == null }
        }

        override fun observeFinished(): Flow<List<SessionEntity>> = flowOf(emptyList())

        override suspend fun finished(): List<SessionEntity> = emptyList()

        override suspend fun paceForBook(bookId: Long) = PaceRow(0, 0)

        override suspend fun paceOverall() = PaceRow(0, 0)
    }

    @Test
    fun listeningKeepsSessionOpenAfterReaderLeaves() = runBlocking {
        val dao = FakeSessionDao()
        val repository = SessionRepository(dao)
        repository.open(BOOK, 0L, SessionHolder.Reader)
        repository.open(BOOK, 1_000L, SessionHolder.Listening)
        repository.close(BOOK, 10_000L, 3, SessionHolder.Reader)
        repository.checkpoint(BOOK, 40_000L, 2, SessionHolder.Listening)
        assertEquals(40_000L, dao.rows.values.single().endTs)
        assertEquals(5, dao.rows.values.single().pagesRead)
        repository.close(BOOK, 70_000L, 4, SessionHolder.Listening)
        val row = dao.rows.values.single()
        assertEquals(0L, row.startTs)
        assertEquals(70_000L, row.endTs)
        assertEquals(7, row.pagesRead)
    }

    @Test
    fun listeningAloneRecordsTime() = runBlocking {
        val dao = FakeSessionDao()
        val repository = SessionRepository(dao)
        repository.open(BOOK, 0L, SessionHolder.Listening)
        repository.close(BOOK, 60_000L, 2, SessionHolder.Listening)
        val row = dao.rows.values.single()
        assertEquals(60_000L, row.endTs)
        assertEquals(2, row.pagesRead)
    }

    @Test
    fun shortListeningIsDiscarded() = runBlocking {
        val dao = FakeSessionDao()
        val repository = SessionRepository(dao)
        repository.open(BOOK, 0L, SessionHolder.Listening)
        repository.close(BOOK, 2_000L, 0, SessionHolder.Listening)
        assertNull(dao.rows.values.firstOrNull())
    }

    @Test
    fun readerCountsIncludeEarlierListening() = runBlocking {
        val dao = FakeSessionDao()
        val repository = SessionRepository(dao)
        repository.open(BOOK, 0L, SessionHolder.Listening)
        repository.checkpoint(BOOK, 30_000L, 4, SessionHolder.Listening)
        val start = repository.open(BOOK, 31_000L, SessionHolder.Reader)
        assertEquals(4, start.basePages)
        assertEquals(0L, start.startTs)
    }

    private companion object {
        const val BOOK = 7L
    }
}

package com.reader343.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reader343.data.db.entity.BookEntity
import com.reader343.data.db.entity.BookmarkEntity
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.HighlightEntity
import com.reader343.data.db.entity.NoteEntity
import com.reader343.data.db.entity.OutlineEntryEntity
import com.reader343.data.db.entity.PaceRow
import com.reader343.data.db.entity.ProgressEntity
import com.reader343.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query(
        """
        SELECT books.*,
            (SELECT COUNT(*) FROM highlights WHERE highlights.bookId = books.id) AS highlightCount,
            (SELECT COUNT(*) FROM notes WHERE notes.bookId = books.id) AS noteCount,
            (SELECT COUNT(*) FROM bookmarks WHERE bookmarks.bookId = books.id) AS bookmarkCount,
            (
                SELECT outline.title FROM outline
                WHERE outline.bookId = books.id AND outline.page <= COALESCE(progress.lastPage, 0)
                ORDER BY outline.page DESC, outline.position DESC LIMIT 1
            ) AS chapterTitle,
            (
                SELECT MIN(outline.page) FROM outline
                WHERE outline.bookId = books.id AND outline.page > COALESCE(progress.lastPage, 0)
            ) AS chapterEndPage,
            (
                SELECT COALESCE(SUM(sessions.endTs - sessions.startTs), 0) FROM sessions
                WHERE sessions.bookId = books.id AND sessions.endTs IS NOT NULL
            ) AS readMs,
            (
                SELECT COALESCE(SUM(sessions.pagesRead), 0) FROM sessions
                WHERE sessions.bookId = books.id AND sessions.endTs IS NOT NULL
            ) AS readPages
        FROM books
        LEFT JOIN progress ON progress.bookId = books.id
        ORDER BY MAX(COALESCE(progress.updatedAt, 0), books.addedAt) DESC
        """,
    )
    fun observeWithProgress(): Flow<List<BookWithProgressRow>>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: Long): ProgressEntity?

    @Query("UPDATE progress SET finishedAt = :finishedAt WHERE bookId = :bookId")
    suspend fun setFinishedAt(bookId: Long, finishedAt: Long?)

    @Query("UPDATE progress SET lastPage = 0, scrollOffset = 0, percent = 0, updatedAt = 0, finishedAt = NULL WHERE bookId = :bookId")
    suspend fun reset(bookId: Long)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY page, createdAt")
    fun observeByBook(bookId: Long): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY page, createdAt")
    fun observeByBook(bookId: Long): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET body = :body WHERE id = :id")
    suspend fun updateBody(id: Long, body: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE bookId = :bookId AND endTs IS NOT NULL ORDER BY endTs DESC LIMIT 1")
    suspend fun latestFinished(bookId: Long): SessionEntity?

    @Query("UPDATE sessions SET endTs = :endTs, pagesRead = :pagesRead WHERE id = :id")
    suspend fun finish(id: Long, endTs: Long, pagesRead: Int)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions WHERE bookId = :bookId AND endTs IS NULL")
    suspend fun deleteUnfinished(bookId: Long)

    @Query("SELECT * FROM sessions WHERE endTs IS NOT NULL ORDER BY startTs")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT startTs FROM sessions WHERE endTs IS NOT NULL")
    suspend fun finishedStartTimes(): List<Long>

    @Query(
        """
        SELECT COALESCE(SUM(endTs - startTs), 0) AS timeMs, COALESCE(SUM(pagesRead), 0) AS pages
        FROM sessions WHERE endTs IS NOT NULL AND bookId = :bookId
        """,
    )
    suspend fun paceForBook(bookId: Long): PaceRow

    @Query(
        """
        SELECT COALESCE(SUM(endTs - startTs), 0) AS timeMs, COALESCE(SUM(pagesRead), 0) AS pages
        FROM sessions WHERE endTs IS NOT NULL
        """,
    )
    suspend fun paceOverall(): PaceRow
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY page")
    fun observeByBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND page = :page")
    suspend fun delete(bookId: Long, page: Int): Int
}

@Dao
interface OutlineDao {
    @Insert
    suspend fun insertAll(entries: List<OutlineEntryEntity>)
}

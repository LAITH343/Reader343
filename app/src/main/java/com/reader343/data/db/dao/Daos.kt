package com.reader343.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reader343.data.db.entity.BookEntity
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Transaction
    @Query(
        """
        SELECT books.* FROM books
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
}

@Dao
interface HighlightDao

@Dao
interface NoteDao

@Dao
interface SessionDao

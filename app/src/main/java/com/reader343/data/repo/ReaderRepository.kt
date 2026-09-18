package com.reader343.data.repo

import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.ProgressDao
import com.reader343.data.db.entity.ProgressEntity
import com.reader343.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderRepository @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
) {

    suspend fun getBook(id: Long): Book? {
        val book = bookDao.getById(id) ?: return null
        val progress = progressDao.getByBookId(id)
        return Book(
            id = book.id,
            title = book.title,
            filePath = book.filePath,
            pageCount = book.pageCount,
            lastPage = progress?.lastPage ?: 0,
        )
    }

    suspend fun saveProgress(bookId: Long, page: Int, pageCount: Int) {
        if (pageCount <= 0) return
        val clamped = page.coerceIn(0, pageCount - 1)
        val now = System.currentTimeMillis()
        val finishedAt = progressDao.getByBookId(bookId)?.finishedAt
            ?: now.takeIf { clamped == pageCount - 1 }
        progressDao.upsert(
            ProgressEntity(
                bookId = bookId,
                lastPage = clamped,
                scrollOffset = 0f,
                percent = (clamped + 1).toFloat() / pageCount,
                updatedAt = now,
                finishedAt = finishedAt,
            ),
        )
    }
}

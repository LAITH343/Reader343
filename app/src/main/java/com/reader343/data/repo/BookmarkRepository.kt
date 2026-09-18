package com.reader343.data.repo

import com.reader343.data.db.dao.BookmarkDao
import com.reader343.data.db.entity.BookmarkEntity
import com.reader343.domain.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) {

    fun observe(bookId: Long): Flow<List<Bookmark>> =
        bookmarkDao.observeByBook(bookId).map { rows ->
            rows.map { Bookmark(id = it.id, page = it.page, createdAt = it.createdAt) }
        }

    suspend fun toggle(bookId: Long, page: Int) {
        if (bookmarkDao.delete(bookId, page) == 0) {
            bookmarkDao.insert(BookmarkEntity(bookId = bookId, page = page, createdAt = System.currentTimeMillis()))
        }
    }

    suspend fun remove(bookId: Long, page: Int) {
        bookmarkDao.delete(bookId, page)
    }
}

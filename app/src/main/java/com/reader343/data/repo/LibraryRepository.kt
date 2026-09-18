package com.reader343.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.reader343.data.db.ReaderDatabase
import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.ProgressDao
import com.reader343.data.db.entity.BookEntity
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.ProgressEntity
import com.reader343.domain.BookWithProgress
import com.reader343.domain.continueCandidate
import com.reader343.pdf.PdfImportReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ReaderDatabase,
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val pdfReader: PdfImportReader,
) {

    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }

    fun observeBooks(): Flow<List<BookWithProgress>> =
        bookDao.observeWithProgress().map { rows -> rows.map { it.toDomain() } }

    suspend fun continueBook(): BookWithProgress? = observeBooks().first().continueCandidate()

    suspend fun importPdf(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val pdfFile = File(booksDir, "$id.pdf")
        val coverFile = File(coversDir, "$id.png")
        try {
            takeReadPermission(uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open $uri")
            input.use { src -> pdfFile.outputStream().use { src.copyTo(it) } }

            val pageCount = pdfReader.readAndRenderCover(pdfFile, coverFile)
            val now = System.currentTimeMillis()
            val bookId = database.withTransaction {
                val bookId = bookDao.insert(
                    BookEntity(
                        title = displayName(uri) ?: pdfFile.nameWithoutExtension,
                        sourceUri = uri.toString(),
                        filePath = pdfFile.absolutePath,
                        pageCount = pageCount,
                        coverPath = coverFile.takeIf { it.exists() }?.absolutePath,
                        addedAt = now,
                    ),
                )
                progressDao.upsert(
                    ProgressEntity(
                        bookId = bookId,
                        lastPage = 0,
                        scrollOffset = 0f,
                        percent = 0f,
                        updatedAt = 0L,
                    ),
                )
                bookId
            }
            Result.success(bookId)
        } catch (e: Throwable) {
            pdfFile.delete()
            coverFile.delete()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun deleteBook(id: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(id) ?: return@withContext
        bookDao.deleteById(id)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(book.sourceUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun takeReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }

    private fun BookWithProgressRow.toDomain() = BookWithProgress(
        id = book.id,
        title = book.title,
        coverPath = book.coverPath,
        pageCount = book.pageCount,
        lastPage = progress?.lastPage ?: 0,
        percent = progress?.percent ?: 0f,
        lastReadAt = progress?.updatedAt?.takeIf { it > 0L },
    )
}

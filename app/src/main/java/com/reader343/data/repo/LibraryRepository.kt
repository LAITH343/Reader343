package com.reader343.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.reader343.data.db.ReaderDatabase
import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.dao.OutlineDao
import com.reader343.data.db.dao.ProgressDao
import com.reader343.data.db.entity.BookEntity
import com.reader343.data.db.entity.BookWithProgressRow
import com.reader343.data.db.entity.OutlineEntryEntity
import com.reader343.data.db.entity.ProgressEntity
import com.reader343.domain.BookInfo
import com.reader343.domain.BookWithProgress
import com.reader343.domain.MetadataProvider
import com.reader343.domain.MetadataStatus
import com.reader343.domain.continueCandidate
import com.reader343.domain.msPerPage
import com.reader343.metadata.MetadataWorker
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
    private val outlineDao: OutlineDao,
    private val pdfReader: PdfImportReader,
    private val settingsRepository: SettingsRepository,
) {

    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }

    fun observeBooks(): Flow<List<BookWithProgress>> =
        bookDao.observeWithProgress().map { rows ->
            val overall = msPerPage(rows.sumOf { it.readMs }, rows.sumOf { it.readPages })
            rows.map { it.toDomain(overall) }
        }

    fun observeBook(id: Long): Flow<BookWithProgress?> =
        observeBooks().map { books -> books.firstOrNull { it.id == id } }

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

            val imported = pdfReader.readAndRenderCover(pdfFile, coverFile)
            val now = System.currentTimeMillis()
            val bookId = database.withTransaction {
                val bookId = bookDao.insert(
                    BookEntity(
                        title = displayName(uri) ?: pdfFile.nameWithoutExtension,
                        sourceUri = uri.toString(),
                        filePath = pdfFile.absolutePath,
                        pageCount = imported.pageCount,
                        coverPath = coverFile.takeIf { it.exists() }?.absolutePath,
                        addedAt = now,
                        isbn = imported.isbn,
                        hasTextLayer = imported.hasTextLayer,
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
                outlineDao.insertAll(
                    imported.outline.mapIndexed { index, entry ->
                        OutlineEntryEntity(
                            bookId = bookId,
                            position = index,
                            title = entry.title,
                            page = entry.page,
                            depth = entry.depth,
                        )
                    },
                )
                bookId
            }
            if (settingsRepository.settings.first().autoFetchMetadata) MetadataWorker.enqueue(context, bookId)
            Result.success(bookId)
        } catch (e: Throwable) {
            pdfFile.delete()
            coverFile.delete()
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun setFinished(id: Long, finished: Boolean) {
        progressDao.setFinishedAt(id, if (finished) System.currentTimeMillis() else null)
    }

    suspend fun resetProgress(id: Long) {
        progressDao.reset(id)
    }

    suspend fun deleteBook(id: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(id) ?: return@withContext
        MetadataWorker.cancel(context, id)
        bookDao.deleteById(id)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        book.metadataCoverPath?.let { File(it).delete() }
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

    private fun BookWithProgressRow.toDomain(overallMsPerPage: Long?) = BookWithProgress(
        id = book.id,
        title = book.title,
        coverPath = book.metadataCoverPath ?: book.coverPath,
        pageCount = book.pageCount,
        lastPage = progress?.lastPage ?: 0,
        percent = progress?.percent ?: 0f,
        lastReadAt = progress?.updatedAt?.takeIf { it > 0L },
        finishedAt = progress?.finishedAt,
        highlightCount = highlightCount,
        noteCount = noteCount,
        bookmarkCount = bookmarkCount,
        chapterTitle = chapterTitle,
        chapterEndPage = chapterEndPage,
        msPerPage = msPerPage(readMs, readPages) ?: overallMsPerPage,
        metadata = BookInfo(
            author = book.author,
            description = book.description,
            publishedYear = book.publishedYear,
            publisher = book.publisher,
            isbn = book.isbn,
            provider = MetadataProvider.fromKey(book.metadataSource),
            fetchedAt = book.metadataFetchedAt,
            status = MetadataStatus.fromKey(book.metadataStatus),
            hasRemoteCover = book.metadataCoverPath != null,
            userEdited = book.userEdited,
        ),
    )
}

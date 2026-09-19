package com.reader343.data.repo

import android.content.Context
import com.reader343.data.db.dao.BookDao
import com.reader343.data.db.entity.BookEntity
import com.reader343.domain.BookMetadata
import com.reader343.domain.LookupResult
import com.reader343.domain.MetadataQuery
import com.reader343.domain.MetadataStatus
import com.reader343.domain.RankedCandidate
import com.reader343.domain.cleanTitleQuery
import com.reader343.domain.rankCandidates
import com.reader343.metadata.Connectivity
import com.reader343.metadata.GoogleBooksProvider
import com.reader343.metadata.MAX_RESULTS
import com.reader343.metadata.MetadataHttp
import com.reader343.metadata.OpenLibraryProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class BookLookup(
    val query: MetadataQuery?,
    val result: LookupResult,
    val candidates: List<RankedCandidate> = emptyList(),
    val bookTitle: String = "",
)

enum class EnrichOutcome { Done, Retry }

@Singleton
class MetadataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val googleBooks: GoogleBooksProvider,
    private val openLibrary: OpenLibraryProvider,
    private val http: MetadataHttp,
    private val connectivity: Connectivity,
) {

    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }
    private val previewsDir get() = File(context.cacheDir, "metadata_previews").apply { mkdirs() }

    fun observeReviewIds(): Flow<List<Long>> = bookDao.observeIdsWithStatus(MetadataStatus.Review.key)

    suspend fun lookup(query: MetadataQuery): LookupResult {
        if (!connectivity.isOnline()) return LookupResult.Offline
        val google = attempt { googleBooks.search(query) }
        google.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return LookupResult.Found(it.take(MAX_RESULTS)) }
        val library = attempt { openLibrary.search(query) }
        library.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return LookupResult.Found(it.take(MAX_RESULTS)) }
        return when {
            google.isSuccess || library.isSuccess -> LookupResult.NoMatch
            !connectivity.isOnline() -> LookupResult.Offline
            else -> LookupResult.Failed
        }
    }

    suspend fun lookupForBook(bookId: Long): BookLookup {
        val book = bookDao.getById(bookId) ?: return BookLookup(null, LookupResult.NoMatch)
        val titleQuery = cleanTitleQuery(book.title).takeIf { it.isNotBlank() }?.let { MetadataQuery.Title(it) }
        val isbnQuery = book.isbn?.let { MetadataQuery.Isbn(it) }
        val queries = listOfNotNull(isbnQuery, titleQuery)
        if (queries.isEmpty()) return BookLookup(null, LookupResult.NoMatch, bookTitle = book.title)
        var last = BookLookup(queries.first(), LookupResult.NoMatch, bookTitle = book.title)
        for (query in queries) {
            val result = lookup(query)
            if (result is LookupResult.Found) {
                return BookLookup(query, result, rankCandidates(query, result.candidates), book.title)
            }
            last = BookLookup(query, result, bookTitle = book.title)
            if (result != LookupResult.NoMatch) return last
        }
        return last
    }

    suspend fun autoEnrich(bookId: Long): EnrichOutcome {
        val book = bookDao.getById(bookId) ?: return EnrichOutcome.Done
        if (book.userEdited || book.metadataStatus != null) return EnrichOutcome.Done
        val lookup = lookupForBook(bookId)
        return when (lookup.result) {
            is LookupResult.Found -> {
                val best = lookup.candidates.first().metadata
                if (lookup.query is MetadataQuery.Isbn) {
                    apply(bookId, best, automatic = true)
                } else {
                    bookDao.setMetadataStatus(bookId, MetadataStatus.Review.key)
                }
                EnrichOutcome.Done
            }
            LookupResult.NoMatch -> {
                if (lookup.query != null) bookDao.setMetadataStatus(bookId, MetadataStatus.None.key)
                EnrichOutcome.Done
            }
            LookupResult.Offline, LookupResult.Failed -> EnrichOutcome.Retry
        }
    }

    suspend fun apply(bookId: Long, candidate: BookMetadata, automatic: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val book = bookDao.getById(bookId) ?: return@withContext false
            if (automatic && book.userEdited) return@withContext false
            val description = candidate.description ?: candidate.workKey?.let { key ->
                attempt { openLibrary.description(key) }.getOrNull()
            }
            val cover = candidate.coverUrl?.let { storeCover(book, it) }
            val current = bookDao.getById(bookId)
            if (current == null || (automatic && current.userEdited)) {
                cover?.delete()
                return@withContext false
            }
            bookDao.update(
                current.copy(
                    title = candidate.title.ifBlank { current.title },
                    author = candidate.author,
                    description = description,
                    publishedYear = candidate.publishedYear,
                    publisher = candidate.publisher,
                    isbn = candidate.isbn ?: current.isbn,
                    remoteCoverUrl = candidate.coverUrl,
                    metadataCoverPath = cover?.absolutePath,
                    metadataSource = candidate.provider.key,
                    metadataFetchedAt = System.currentTimeMillis(),
                    metadataStatus = MetadataStatus.Applied.key,
                ),
            )
            current.metadataCoverPath?.let { File(it).delete() }
            true
        }

    suspend fun editDetails(bookId: Long, title: String, author: String?) {
        val book = bookDao.getById(bookId) ?: return
        val cleanTitle = title.trim().ifBlank { book.title }
        val cleanAuthor = author?.trim()?.takeIf { it.isNotEmpty() }
        bookDao.update(book.copy(title = cleanTitle, author = cleanAuthor, userEdited = true))
    }

    suspend fun previewCover(url: String): File? = withContext(Dispatchers.IO) {
        val file = File(previewsDir, hash(url))
        if (file.exists() || http.download(url, file)) file else null
    }

    private suspend fun storeCover(book: BookEntity, url: String): File? {
        val target = File(coversDir, "meta-${book.id}-${System.currentTimeMillis()}.img")
        val preview = File(previewsDir, hash(url))
        val stored = if (preview.exists()) {
            runCatching { preview.copyTo(target, overwrite = true) }.isSuccess
        } else {
            http.download(url, target)
        }
        return target.takeIf { stored && it.exists() }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private inline fun <T> attempt(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}

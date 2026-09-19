package com.reader343.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import com.reader343.di.PdfDispatcher
import com.reader343.domain.OutlineEntry
import com.reader343.domain.detectTextLayer
import com.reader343.domain.findIsbn
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPdf(
    val pageCount: Int,
    val outline: List<OutlineEntry>,
    val isbn: String? = null,
    val hasTextLayer: Boolean? = null,
)

@Singleton
class PdfImportReader @Inject constructor(
    private val core: PdfiumCore,
    @PdfDispatcher private val dispatcher: CoroutineDispatcher,
) {

    suspend fun readAndRenderCover(pdf: File, cover: File, coverWidth: Int = COVER_WIDTH): ImportedPdf =
        withContext(dispatcher) {
            val fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val document = try {
                core.newDocument(fd)
            } catch (e: Throwable) {
                fd.close()
                throw e
            }
            try {
                val pageCount = document.getPageCount()
                if (pageCount > 0) document.openPage(0)?.let { renderCover(it, cover, coverWidth) }
                val outline = runCatching { document.getTableOfContents().flatten(pageCount) }.getOrDefault(emptyList())
                val isbn = runCatching { findIsbnIn(document, pageCount) }.getOrNull()
                val hasTextLayer = runCatching { detectTextLayer(pageCount) { pageText(document, it) } }.getOrNull()
                ImportedPdf(pageCount, outline, isbn, hasTextLayer)
            } finally {
                document.close()
            }
        }

    private fun findIsbnIn(document: PdfDocument, pageCount: Int): String? {
        for (index in 0 until minOf(pageCount, ISBN_PAGES)) {
            val text = pageText(document, index) ?: continue
            findIsbn(text)?.let { return it }
        }
        return null
    }

    private fun pageText(document: PdfDocument, index: Int): String? =
        document.openPage(index)?.use { page -> page.extractText() }

    private fun renderCover(page: PdfPage, cover: File, width: Int) {
        try {
            val widthPt = page.getPageWidthPoint().coerceAtLeast(1)
            val heightPt = page.getPageHeightPoint().coerceAtLeast(1)
            val height = (width.toLong() * heightPt / widthPt).toInt().coerceIn(1, width * 4)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.renderPageBitmap(bitmap, 0, 0, width, height)
                cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.close()
        }
    }

    private companion object {
        const val COVER_WIDTH = 480
        const val ISBN_PAGES = 6
    }
}

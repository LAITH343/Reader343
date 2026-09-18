package com.reader343.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import com.reader343.di.PdfDispatcher
import com.reader343.domain.OutlineEntry
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPdf(val pageCount: Int, val outline: List<OutlineEntry>)

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
                ImportedPdf(pageCount, outline)
            } finally {
                document.close()
            }
        }

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
    }
}

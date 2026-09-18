package com.reader343.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.reader343.di.PdfDispatcher
import com.reader343.domain.NormRect
import com.reader343.domain.OutlineEntry
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

data class PageSize(val widthPt: Float, val heightPt: Float) {
    val aspectRatio: Float get() = widthPt / heightPt
}

class PdfEngine @Inject constructor(
    private val core: PdfiumCore,
    @PdfDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private var document: PdfDocument? = null
    private var pageSizes: List<PageSize> = emptyList()

    val pageCount: Int get() = pageSizes.size

    suspend fun open(filePath: String) = withContext(dispatcher) {
        check(document == null) { "Document already open" }
        val fd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        val doc = try {
            core.newDocument(fd)
        } catch (e: Throwable) {
            fd.close()
            throw e
        }
        pageSizes = doc.getPageSizes(POINTS_DPI).map { size ->
            PageSize(
                widthPt = size.width.coerceAtLeast(1).toFloat(),
                heightPt = size.height.coerceAtLeast(1).toFloat(),
            )
        }
        document = doc
    }

    fun pageSize(index: Int): PageSize = pageSizes[index]

    fun pageSizes(): List<PageSize> = pageSizes

    suspend fun renderPage(index: Int, dpi: Float, region: RectF = FULL_PAGE): Bitmap =
        withContext(dispatcher) {
            val doc = checkNotNull(document) { "Document not open" }
            val size = pageSizes[index]
            val fullWidth = (size.widthPt * dpi / POINTS_DPI).roundToInt().coerceAtLeast(1)
            val fullHeight = (size.heightPt * dpi / POINTS_DPI).roundToInt().coerceAtLeast(1)
            val left = (region.left * fullWidth).roundToInt()
            val top = (region.top * fullHeight).roundToInt()
            val width = ((region.right * fullWidth).roundToInt() - left).coerceAtLeast(1)
            val height = ((region.bottom * fullHeight).roundToInt() - top).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            doc.openPage(index)?.use { page ->
                page.renderPageBitmap(bitmap, -left, -top, fullWidth, fullHeight)
            }
            bitmap
        }

    suspend fun outline(): List<OutlineEntry> = withContext(dispatcher) {
        val doc = checkNotNull(document) { "Document not open" }
        runCatching { doc.getTableOfContents().flatten(pageSizes.size) }.getOrDefault(emptyList())
    }

    suspend fun loadText(index: Int): PageText = withContext(dispatcher) {
        val doc = checkNotNull(document) { "Document not open" }
        val page = doc.openPage(index) ?: return@withContext PageText(index, emptyList())
        page.use {
            val size = pageSizes[index]
            val probeX = size.widthPt.toDouble()
            val probeY = size.heightPt.toDouble()
            val origin = page.mapPageCoordsToDevice(0, 0, DEVICE_UNITS, DEVICE_UNITS, 0, 0.0, 0.0)
            val alongX = page.mapPageCoordsToDevice(0, 0, DEVICE_UNITS, DEVICE_UNITS, 0, probeX, 0.0)
            val alongY = page.mapPageCoordsToDevice(0, 0, DEVICE_UNITS, DEVICE_UNITS, 0, 0.0, probeY)
            val xx = (alongX.x - origin.x) / (probeX * DEVICE_UNITS)
            val xy = (alongY.x - origin.x) / (probeY * DEVICE_UNITS)
            val yx = (alongX.y - origin.y) / (probeX * DEVICE_UNITS)
            val yy = (alongY.y - origin.y) / (probeY * DEVICE_UNITS)
            val ox = origin.x.toDouble() / DEVICE_UNITS
            val oy = origin.y.toDouble() / DEVICE_UNITS

            page.openTextPage().use { text ->
                val count = text.textPageCountChars().coerceAtLeast(0)
                val chars = List(count) { i ->
                    val box = text.textPageGetCharBox(i)?.let { b ->
                        val x1 = (ox + xx * b.left + xy * b.top).toFloat()
                        val y1 = (oy + yx * b.left + yy * b.top).toFloat()
                        val x2 = (ox + xx * b.right + xy * b.bottom).toFloat()
                        val y2 = (oy + yx * b.right + yy * b.bottom).toFloat()
                        NormRect.spanning(x1, y1, x2, y2).takeUnless { it.isEmpty }
                    }
                    TextChar(text.textPageGetUnicode(i), box)
                }
                PageText(index, chars)
            }
        }
    }

    suspend fun close() = withContext(dispatcher) {
        document?.close()
        document = null
        pageSizes = emptyList()
    }

    companion object {
        const val POINTS_DPI = 72
        private const val DEVICE_UNITS = 1_000_000
        val FULL_PAGE = RectF(0f, 0f, 1f, 1f)
    }
}

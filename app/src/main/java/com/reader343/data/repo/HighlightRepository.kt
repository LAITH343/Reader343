package com.reader343.data.repo

import com.reader343.data.db.dao.HighlightDao
import com.reader343.data.db.entity.HighlightEntity
import com.reader343.domain.Highlight
import com.reader343.domain.NewHighlight
import com.reader343.domain.NormRect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
) {

    fun observeByPage(bookId: Long): Flow<Map<Int, List<Highlight>>> =
        highlightDao.observeByBook(bookId).map { rows ->
            rows.mapNotNull { it.toDomain() }.groupBy { it.page }
        }

    suspend fun add(bookId: Long, highlight: NewHighlight): Long =
        highlightDao.insert(
            HighlightEntity(
                bookId = bookId,
                page = highlight.page,
                rectsJson = encodeRects(highlight.rects),
                color = highlight.color,
                charStart = highlight.charStart,
                charEnd = highlight.charEnd,
                snippet = highlight.snippet,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun delete(id: Long) = highlightDao.deleteById(id)

    private fun HighlightEntity.toDomain(): Highlight? {
        val rects = decodeRects(rectsJson) ?: return null
        return Highlight(
            id = id,
            page = page,
            rects = rects,
            color = color,
            charStart = charStart,
            charEnd = charEnd,
            snippet = snippet,
        )
    }

    private fun encodeRects(rects: List<NormRect>): String {
        val array = JSONArray()
        rects.forEach { rect ->
            array.put(
                JSONArray()
                    .put(rect.left.toDouble())
                    .put(rect.top.toDouble())
                    .put(rect.right.toDouble())
                    .put(rect.bottom.toDouble()),
            )
        }
        return array.toString()
    }

    private fun decodeRects(json: String): List<NormRect>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { i ->
            val item = array.getJSONArray(i)
            NormRect(
                left = item.getDouble(0).toFloat(),
                top = item.getDouble(1).toFloat(),
                right = item.getDouble(2).toFloat(),
                bottom = item.getDouble(3).toFloat(),
            )
        }
    }.getOrNull()
}

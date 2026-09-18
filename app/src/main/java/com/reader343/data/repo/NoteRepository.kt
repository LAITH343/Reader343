package com.reader343.data.repo

import com.reader343.data.db.dao.NoteDao
import com.reader343.data.db.entity.NoteEntity
import com.reader343.domain.Note
import com.reader343.domain.NoteAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
) {

    fun observe(bookId: Long): Flow<List<Note>> =
        noteDao.observeByBook(bookId).map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun add(bookId: Long, page: Int, anchor: NoteAnchor, body: String): Long =
        noteDao.insert(
            NoteEntity(
                bookId = bookId,
                page = page,
                anchorJson = encodeAnchor(page, anchor),
                body = body,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun updateBody(id: Long, body: String) = noteDao.updateBody(id, body)

    suspend fun delete(id: Long) = noteDao.deleteById(id)

    private fun NoteEntity.toDomain(): Note? {
        val anchor = decodeAnchor(anchorJson) ?: return null
        return Note(id = id, page = page, anchor = anchor, body = body, createdAt = createdAt)
    }

    private fun encodeAnchor(page: Int, anchor: NoteAnchor): String {
        val json = JSONObject()
            .put(KEY_PAGE, page)
            .put(KEY_RECT, anchor.rect.toJson())
        anchor.highlightId?.let { json.put(KEY_HIGHLIGHT, it) }
        anchor.snippet?.let { json.put(KEY_SNIPPET, it) }
        return json.toString()
    }

    private fun decodeAnchor(json: String): NoteAnchor? = runCatching {
        val obj = JSONObject(json)
        NoteAnchor(
            rect = obj.getJSONArray(KEY_RECT).toNormRect(),
            highlightId = if (obj.has(KEY_HIGHLIGHT)) obj.getLong(KEY_HIGHLIGHT) else null,
            snippet = if (obj.has(KEY_SNIPPET)) obj.getString(KEY_SNIPPET) else null,
        )
    }.getOrNull()

    private companion object {
        const val KEY_PAGE = "page"
        const val KEY_RECT = "rect"
        const val KEY_HIGHLIGHT = "highlightId"
        const val KEY_SNIPPET = "snippet"
    }
}

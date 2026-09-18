package com.reader343.ui.reader

import com.reader343.domain.Note
import com.reader343.domain.NoteAnchor

data class NoteEditor(
    val key: Long,
    val noteId: Long?,
    val page: Int,
    val anchor: NoteAnchor,
    val body: String,
)

data class NotesUiState(
    val byPage: Map<Int, List<Note>> = emptyMap(),
    val editor: NoteEditor? = null,
    val loaded: Boolean = false,
) {
    fun forHighlight(highlightId: Long): Note? =
        byPage.values.firstNotNullOfOrNull { notes -> notes.firstOrNull { it.anchor.highlightId == highlightId } }
}

interface NoteActions {
    fun onAddNoteFromSelection()
    fun onAddNoteFromHighlight()
    fun onOpenNote(noteId: Long)
    fun onSaveNote(body: String)
    fun onDeleteNote()
    fun onDismissNote()

    companion object {
        val None = object : NoteActions {
            override fun onAddNoteFromSelection() = Unit
            override fun onAddNoteFromHighlight() = Unit
            override fun onOpenNote(noteId: Long) = Unit
            override fun onSaveNote(body: String) = Unit
            override fun onDeleteNote() = Unit
            override fun onDismissNote() = Unit
        }
    }
}

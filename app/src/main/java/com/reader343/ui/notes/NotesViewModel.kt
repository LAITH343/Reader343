package com.reader343.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.HighlightRepository
import com.reader343.data.repo.NoteRepository
import com.reader343.data.repo.ReaderRepository
import com.reader343.domain.Mark
import com.reader343.domain.MarkFilter
import com.reader343.domain.NormRect
import com.reader343.domain.NoteAnchor
import com.reader343.domain.buildMarks
import com.reader343.domain.filteredBy
import com.reader343.ui.nav.Routes
import com.reader343.ui.reader.NoteEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotesUiState {
    data object Loading : NotesUiState
    data object Error : NotesUiState
    data class Content(
        val title: String,
        val marks: List<Mark>,
        val filter: MarkFilter,
        val editor: NoteEditor? = null,
    ) : NotesUiState {
        val highlightCount: Int get() = marks.count { it.highlight != null }
        val noteCount: Int get() = marks.count { it.note != null }
        val visible: List<Mark> get() = marks.filteredBy(filter)
    }
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    readerRepository: ReaderRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])
    private val filter = MutableStateFlow(MarkFilter.All)
    private val editor = MutableStateFlow<NoteEditor?>(null)
    private var editorKey = 0L

    private val title = flow { emit(readerRepository.getBook(bookId)?.title) }

    val uiState: StateFlow<NotesUiState> = combine(
        title,
        highlightRepository.observe(bookId),
        noteRepository.observe(bookId),
        filter,
        editor,
    ) { title, highlights, notes, filter, editor ->
        if (title == null) {
            NotesUiState.Error
        } else {
            NotesUiState.Content(
                title = title,
                marks = buildMarks(highlights, notes),
                filter = filter,
                editor = editor?.takeIf { e -> e.noteId == null || notes.any { it.id == e.noteId } },
            )
        }
    }
        .catch { emit(NotesUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotesUiState.Loading)

    fun onFilter(value: MarkFilter) {
        filter.value = value
    }

    fun onEditNote(mark: Mark) {
        val note = mark.note
        if (note != null) {
            startEditor(noteId = note.id, page = note.page, anchor = note.anchor, body = note.body)
            return
        }
        val highlight = mark.highlight ?: return
        val rect = highlight.rects.reduceOrNull(NormRect::union) ?: return
        startEditor(
            noteId = null,
            page = highlight.page,
            anchor = NoteAnchor(rect = rect, highlightId = highlight.id, snippet = highlight.snippet),
            body = "",
        )
    }

    fun onSaveNote(body: String) {
        val current = editor.value ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        editor.value = null
        viewModelScope.launch {
            if (current.noteId == null) {
                noteRepository.add(bookId, current.page, current.anchor, text)
            } else {
                noteRepository.updateBody(current.noteId, text)
            }
        }
    }

    fun onDeleteEditedNote() {
        val noteId = editor.value?.noteId
        editor.value = null
        if (noteId != null) viewModelScope.launch { noteRepository.delete(noteId) }
    }

    fun onDismissEditor() {
        editor.value = null
    }

    fun onChangeColor(mark: Mark, color: Int) {
        val highlight = mark.highlight ?: return
        if (highlight.color == color) return
        viewModelScope.launch { highlightRepository.updateColor(highlight.id, color) }
    }

    fun onDelete(mark: Mark) {
        viewModelScope.launch {
            mark.note?.let { noteRepository.delete(it.id) }
            mark.highlight?.let { highlightRepository.delete(it.id) }
        }
    }

    private fun startEditor(noteId: Long?, page: Int, anchor: NoteAnchor, body: String) {
        editorKey++
        editor.update { NoteEditor(key = editorKey, noteId = noteId, page = page, anchor = anchor, body = body) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

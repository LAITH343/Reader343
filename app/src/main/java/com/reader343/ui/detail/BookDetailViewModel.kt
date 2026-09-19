package com.reader343.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.LibraryRepository
import com.reader343.domain.BookWithProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data object Missing : BookDetailUiState
    data class Content(val book: BookWithProgress) : BookDetailUiState
}

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
) : ViewModel() {

    val bookId: Long = savedStateHandle.get<Long>(ARG_BOOK_ID) ?: -1L

    val uiState: StateFlow<BookDetailUiState> = repository.observeBook(bookId)
        .map { book -> if (book == null) BookDetailUiState.Missing else BookDetailUiState.Content(book) }
        .catch { emit(BookDetailUiState.Missing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState.Loading)

    fun setFinished(id: Long, finished: Boolean) {
        viewModelScope.launch { repository.setFinished(id, finished) }
    }

    fun resetProgress(id: Long) {
        viewModelScope.launch { repository.resetProgress(id) }
    }

    fun deleteBook(id: Long) {
        viewModelScope.launch { repository.deleteBook(id) }
    }

    private companion object {
        const val ARG_BOOK_ID = "bookId"
    }
}

package com.reader343.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.LibraryRepository
import com.reader343.domain.BookWithProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data object Error : LibraryUiState
    data class Content(val books: List<BookWithProgress>) : LibraryUiState
}

sealed interface LibraryEvent {
    data object ImportFailed : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = reload
        .flatMapLatest { attempt ->
            repository.observeBooks()
                .map<List<BookWithProgress>, LibraryUiState> { books ->
                    if (books.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(books)
                }
                .onStart { if (attempt > 0) emit(LibraryUiState.Loading) }
                .catch { emit(LibraryUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    private val pendingImports = MutableStateFlow(0)
    val importing: StateFlow<Boolean> = pendingImports
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            pendingImports.update { it + 1 }
            try {
                repository.importPdf(uri).onFailure { _events.send(LibraryEvent.ImportFailed) }
            } finally {
                pendingImports.update { it - 1 }
            }
        }
    }

    fun retry() {
        reload.update { it + 1 }
    }

    fun deleteBook(id: Long) {
        viewModelScope.launch { repository.deleteBook(id) }
    }
}

package com.reader343.ui.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.LibraryRepository
import com.reader343.data.repo.MetadataRepository
import com.reader343.domain.LookupResult
import com.reader343.domain.RankedCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickerCandidate(
    val ranked: RankedCandidate,
    val coverPath: String? = null,
)

sealed interface PickerState {
    val bookId: Long

    data class Loading(override val bookId: Long) : PickerState
    data class Results(
        override val bookId: Long,
        val bookTitle: String,
        val candidates: List<PickerCandidate>,
        val selected: Int = 0,
        val applying: Boolean = false,
    ) : PickerState
    data class Empty(override val bookId: Long) : PickerState
    data class Offline(override val bookId: Long) : PickerState
    data class Failed(override val bookId: Long) : PickerState
}

data class EditTarget(
    val bookId: Long,
    val title: String,
    val author: String?,
    val searchAfterSave: Boolean,
)

sealed interface MetadataEvent {
    data object Applied : MetadataEvent
    data object ApplyFailed : MetadataEvent
}

@HiltViewModel
class MetadataPickerViewModel @Inject constructor(
    private val repository: MetadataRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _picker = MutableStateFlow<PickerState?>(null)
    val picker: StateFlow<PickerState?> = _picker.asStateFlow()

    private val _edit = MutableStateFlow<EditTarget?>(null)
    val edit: StateFlow<EditTarget?> = _edit.asStateFlow()

    private val _events = Channel<MetadataEvent>(Channel.BUFFERED)
    val events: Flow<MetadataEvent> = _events.receiveAsFlow()

    private var lookupJob: Job? = null

    fun openPicker(bookId: Long) {
        lookupJob?.cancel()
        _picker.value = PickerState.Loading(bookId)
        lookupJob = viewModelScope.launch {
            val lookup = repository.lookupForBook(bookId)
            val state = when (val result = lookup.result) {
                is LookupResult.Found -> PickerState.Results(
                    bookId = bookId,
                    bookTitle = lookup.bookTitle,
                    candidates = lookup.candidates.map { PickerCandidate(it) },
                )
                LookupResult.NoMatch -> PickerState.Empty(bookId)
                LookupResult.Offline -> PickerState.Offline(bookId)
                LookupResult.Failed -> PickerState.Failed(bookId)
            }
            if (_picker.value?.bookId != bookId) return@launch
            _picker.value = state
            if (state is PickerState.Results) loadCovers(bookId, state.candidates)
        }
    }

    private suspend fun loadCovers(bookId: Long, candidates: List<PickerCandidate>) {
        candidates.forEachIndexed { index, candidate ->
            val url = candidate.ranked.metadata.coverUrl ?: return@forEachIndexed
            val file = repository.previewCover(url) ?: return@forEachIndexed
            _picker.update { state ->
                if (state !is PickerState.Results || state.bookId != bookId) return@update state
                state.copy(
                    candidates = state.candidates.mapIndexed { i, c ->
                        if (i == index) c.copy(coverPath = file.absolutePath) else c
                    },
                )
            }
        }
    }

    fun select(index: Int) {
        _picker.update { state ->
            if (state is PickerState.Results && !state.applying) state.copy(selected = index) else state
        }
    }

    fun useSelected() {
        val state = _picker.value as? PickerState.Results ?: return
        if (state.applying) return
        val candidate = state.candidates.getOrNull(state.selected) ?: return
        _picker.value = state.copy(applying = true)
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val applied = repository.apply(state.bookId, candidate.ranked.metadata)
            _picker.value = null
            _events.send(if (applied) MetadataEvent.Applied else MetadataEvent.ApplyFailed)
        }
    }

    fun retry() {
        _picker.value?.let { openPicker(it.bookId) }
    }

    fun dismissPicker() {
        if ((_picker.value as? PickerState.Results)?.applying == true) return
        lookupJob?.cancel()
        _picker.value = null
    }

    fun startEdit(bookId: Long, searchAfterSave: Boolean = false) {
        viewModelScope.launch {
            val book = libraryRepository.observeBook(bookId).first() ?: return@launch
            if (searchAfterSave) dismissPicker()
            _edit.value = EditTarget(bookId, book.title, book.metadata.author, searchAfterSave)
        }
    }

    fun saveEdit(title: String, author: String) {
        val target = _edit.value ?: return
        _edit.value = null
        viewModelScope.launch {
            repository.editDetails(target.bookId, title, author)
            if (target.searchAfterSave) openPicker(target.bookId)
        }
    }

    fun cancelEdit() {
        _edit.value = null
    }
}

package com.reader343.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.LibraryRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.StatsRepository
import com.reader343.domain.AppSettings
import com.reader343.domain.BookWithProgress
import com.reader343.domain.DailyGoal
import com.reader343.domain.LibraryFilter
import com.reader343.domain.ReadingStats
import com.reader343.domain.WeekDay
import com.reader343.domain.continueCandidate
import com.reader343.domain.filteredBy
import com.reader343.domain.weekProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data object Error : LibraryUiState
    data class Content(
        val books: List<BookWithProgress>,
        val continueBook: BookWithProgress?,
        val stats: HomeStats?,
        val filter: LibraryFilter,
    ) : LibraryUiState {
        val visibleBooks: List<BookWithProgress> get() = books.filteredBy(filter)
        val inProgressCount: Int get() = books.count { it.inProgress }
        val shelf: List<BookWithProgress> get() = books.filterNot { it.finished }.ifEmpty { books }
    }
}

data class HomeStats(
    val streakDays: Int,
    val todayMs: Long,
    val todayPages: Int,
    val goal: DailyGoal,
    val week: List<WeekDay>,
)

sealed interface LibraryEvent {
    data object ImportFailed : LibraryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = reload
        .flatMapLatest { attempt ->
            combine(
                repository.observeBooks(),
                observeHomeStats(),
                savedStateHandle.getStateFlow<LibraryFilter?>(KEY_FILTER, null),
            ) { books, stats, selected ->
                if (books.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    val filter = selected ?: if (books.any { it.inProgress }) LibraryFilter.Reading else LibraryFilter.All
                    LibraryUiState.Content(books, books.continueCandidate(), stats, filter)
                }
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

    private fun observeHomeStats(): Flow<HomeStats?> =
        combine<ReadingStats, AppSettings, HomeStats?>(
            statsRepository.observeStats(),
            settingsRepository.settings,
        ) { stats, settings ->
            stats.toHomeStats(LocalDate.now(), settings.goal)
        }.catch { emit(null) }

    private fun ReadingStats.toHomeStats(today: LocalDate, goal: DailyGoal): HomeStats {
        val day = days.lastOrNull { it.date == today }
        return HomeStats(
            streakDays = streakDays,
            todayMs = day?.timeMs ?: 0L,
            todayPages = day?.pages ?: 0,
            goal = goal,
            week = weekProgress(days, goal, today),
        )
    }

    fun setFilter(filter: LibraryFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    fun setFinished(id: Long, finished: Boolean) {
        viewModelScope.launch { repository.setFinished(id, finished) }
    }

    fun resetProgress(id: Long) {
        viewModelScope.launch { repository.resetProgress(id) }
    }

    fun retry() {
        reload.update { it + 1 }
    }

    fun deleteBook(id: Long) {
        viewModelScope.launch { repository.deleteBook(id) }
    }

    private companion object {
        const val KEY_FILTER = "filter"
    }
}

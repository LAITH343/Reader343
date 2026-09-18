package com.reader343.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.StatsRepository
import com.reader343.domain.ReadingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ChartMetric { Time, Pages }

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Empty : StatsUiState
    data class Content(val stats: ReadingStats, val metric: ChartMetric) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: StatsRepository,
) : ViewModel() {

    private val metric = MutableStateFlow(ChartMetric.Time)

    val uiState: StateFlow<StatsUiState> = combine(repository.observeStats(), metric) { stats, metric ->
        if (stats.sessionCount == 0 && stats.books.isEmpty()) {
            StatsUiState.Empty
        } else {
            StatsUiState.Content(stats, metric)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun onMetricSelected(value: ChartMetric) {
        metric.value = value
    }
}

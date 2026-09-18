package com.reader343.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.StatsRepository
import com.reader343.domain.ActivityMetric
import com.reader343.domain.ReadingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

enum class ChartMetric { Time, Pages }

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Empty : StatsUiState
    data object Error : StatsUiState
    data class Content(
        val stats: ReadingStats,
        val metric: ChartMetric,
        val activity: Map<LocalDate, Int>,
        val today: LocalDate,
    ) : StatsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: StatsRepository,
) : ViewModel() {

    private val metric = MutableStateFlow(ChartMetric.Time)
    private val reload = MutableStateFlow(0)

    val uiState: StateFlow<StatsUiState> = reload
        .flatMapLatest { attempt ->
            val activity = metric.flatMapLatest { selected ->
                repository.observeDailyActivity(selected.toActivityMetric()).map { selected to it }
            }
            combine(repository.observeStats(), activity) { stats, (metric, values) ->
                if (stats.sessionCount == 0 && stats.books.isEmpty()) {
                    StatsUiState.Empty
                } else {
                    StatsUiState.Content(stats, metric, values, LocalDate.now())
                }
            }
                .onStart { if (attempt > 0) emit(StatsUiState.Loading) }
                .catch { emit(StatsUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun onMetricSelected(value: ChartMetric) {
        metric.value = value
    }

    private fun ChartMetric.toActivityMetric() = when (this) {
        ChartMetric.Time -> ActivityMetric.Minutes
        ChartMetric.Pages -> ActivityMetric.Pages
    }

    fun retry() {
        reload.update { it + 1 }
    }
}

package com.reader343.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.StatsRepository
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyGoalUiState(
    val goal: DailyGoal,
    val context: GoalContext,
    val streakDays: Int,
)

@HiltViewModel
class DailyGoalViewModel @Inject constructor(
    private val repository: SettingsRepository,
    statsRepository: StatsRepository,
) : ViewModel() {

    val uiState: StateFlow<DailyGoalUiState?> = combine(
        repository.settings,
        statsRepository.observeGoalContext(),
        statsRepository.observeStreakDays(),
    ) { settings, context, streakDays -> DailyGoalUiState(settings.goal, context, streakDays) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(goal: DailyGoal) {
        viewModelScope.launch { repository.setGoal(goal) }
    }
}

package com.reader343.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.MetadataRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.StatsRepository
import com.reader343.data.repo.UpdateRepository
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.GoalContext
import com.reader343.domain.PageAppearance
import com.reader343.domain.ThemeMode
import com.reader343.update.UpdateSummary
import com.reader343.update.summary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings,
    val goalContext: GoalContext,
    val reviewBookIds: List<Long> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    statsRepository: StatsRepository,
    metadataRepository: MetadataRepository,
    updateRepository: UpdateRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState?> = combine(
        repository.settings,
        statsRepository.observeGoalContext(),
        metadataRepository.observeReviewIds(),
        ::SettingsUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val update: StateFlow<UpdateSummary?> = updateRepository.status
        .map { it.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(value: ThemeMode) = update { repository.setTheme(value) }

    fun setPageAppearance(value: PageAppearance) = update { repository.setPageAppearance(value) }

    fun setLanguage(value: AppLanguage) = update {
        repository.setLanguage(value)
        AppLocales.apply(value)
    }

    fun setDailyReminder(value: Boolean) = update { repository.setDailyReminder(value) }

    fun setStreakAlert(value: Boolean) = update { repository.setStreakAlert(value) }

    fun setReminderTime(value: LocalTime) = update { repository.setReminderTime(value) }

    fun setAutoFetchMetadata(value: Boolean) = update { repository.setAutoFetchMetadata(value) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

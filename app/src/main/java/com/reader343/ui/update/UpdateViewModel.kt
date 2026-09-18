package com.reader343.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.UpdateRepository
import com.reader343.data.repo.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    val uiState: StateFlow<UpdateStatus?> = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { repository.checkIfStale() }
    }

    fun check() {
        viewModelScope.launch { repository.check() }
    }

    fun download(allowMetered: Boolean = false) = repository.startDownload(allowMetered)

    fun pause() = repository.pauseDownload()

    fun cancel() = repository.cancelDownload()
}

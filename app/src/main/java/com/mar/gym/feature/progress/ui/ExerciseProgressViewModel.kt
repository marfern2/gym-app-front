package com.mar.gym.feature.progress.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.PersonalRecords
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExerciseProgressUiState(
    val loading: Boolean = true,
    val sessions: List<ExercisePerformanceSession> = emptyList(),
    val historyError: NetworkFailure? = null,
    val loadingMore: Boolean = false,
    val nextPage: Int = 0,
    val hasMore: Boolean = true,
    val recordsLoading: Boolean = true,
    val records: PersonalRecords? = null,
    val recordsError: NetworkFailure? = null,
)

class ExerciseProgressViewModel(
    private val exerciseTemplateId: String,
    private val repository: AnalyticsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExerciseProgressUiState())
    val uiState: StateFlow<ExerciseProgressUiState> = _uiState.asStateFlow()
    private var historyJob: Job? = null

    init { refresh() }

    fun refresh() {
        historyJob?.cancel()
        _uiState.value = ExerciseProgressUiState()
        loadRecords()
        loadPage(0)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loading || state.loadingMore || state.historyError != null) return
        loadPage(state.nextPage)
    }

    fun retryHistory() {
        val state = _uiState.value
        loadPage(if (state.sessions.isEmpty()) 0 else state.nextPage)
    }

    fun retryRecords() = loadRecords()

    private fun loadPage(page: Int) {
        _uiState.value = if (page == 0) _uiState.value.copy(loading = true, historyError = null)
        else _uiState.value.copy(loadingMore = true, historyError = null)
        historyJob = viewModelScope.launch {
            when (val result = repository.exerciseHistory(exerciseTemplateId, page)) {
                is AnalyticsResult.Failure -> _uiState.value = _uiState.value.copy(
                    loading = false, loadingMore = false, historyError = result.error,
                )
                is AnalyticsResult.Success -> {
                    if (result.value.page != page) {
                        _uiState.value = _uiState.value.copy(
                            loading = false, loadingMore = false,
                            historyError = NetworkFailure.InvalidResponse(),
                        )
                    } else {
                        val sessions = if (page == 0) result.value.content
                        else (_uiState.value.sessions + result.value.content).distinctBy { it.workoutId }
                        _uiState.value = _uiState.value.copy(
                            loading = false, loadingMore = false, sessions = sessions,
                            nextPage = page + 1, hasMore = !result.value.last,
                        )
                    }
                }
            }
        }
    }

    private fun loadRecords() {
        _uiState.value = _uiState.value.copy(recordsLoading = true, recordsError = null)
        viewModelScope.launch {
            when (val result = repository.personalRecords(exerciseTemplateId)) {
                is AnalyticsResult.Failure -> _uiState.value = _uiState.value.copy(
                    recordsLoading = false, recordsError = result.error,
                )
                is AnalyticsResult.Success -> _uiState.value = _uiState.value.copy(
                    recordsLoading = false, records = result.value,
                )
            }
        }
    }
}

class ExerciseProgressViewModelFactory(
    private val exerciseTemplateId: String,
    private val repository: AnalyticsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ExerciseProgressViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ExerciseProgressViewModel(exerciseTemplateId, repository) as T
    }
}

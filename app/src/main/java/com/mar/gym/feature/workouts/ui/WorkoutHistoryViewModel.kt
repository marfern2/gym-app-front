package com.mar.gym.feature.workouts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutHistoryData(
    val items: List<WorkoutHistoryItem> = emptyList(),
    val nextPage: Int = 0,
    val hasNextPage: Boolean = true,
)

sealed interface WorkoutHistoryUiState {
    val data: WorkoutHistoryData
    data class Loading(override val data: WorkoutHistoryData = WorkoutHistoryData()) : WorkoutHistoryUiState
    data class Empty(override val data: WorkoutHistoryData) : WorkoutHistoryUiState
    data class Content(override val data: WorkoutHistoryData) : WorkoutHistoryUiState
    data class Error(override val data: WorkoutHistoryData, val error: WorkoutUiError) : WorkoutHistoryUiState
    data class LoadingMore(override val data: WorkoutHistoryData, val page: Int) : WorkoutHistoryUiState
    data class ErrorLoadingMore(
        override val data: WorkoutHistoryData,
        val page: Int,
        val error: WorkoutUiError,
    ) : WorkoutHistoryUiState
}

class WorkoutHistoryViewModel(private val repository: WorkoutRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<WorkoutHistoryUiState>(WorkoutHistoryUiState.Loading())
    val uiState: StateFlow<WorkoutHistoryUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        job?.cancel()
        _uiState.value = WorkoutHistoryUiState.Loading()
        job = viewModelScope.launch { loadFirst() }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state !is WorkoutHistoryUiState.Content || !state.data.hasNextPage || job?.isActive == true) return
        val page = state.data.nextPage
        _uiState.value = WorkoutHistoryUiState.LoadingMore(state.data, page)
        job = viewModelScope.launch { loadPage(page, state.data) }
    }

    fun retry() {
        when (val state = _uiState.value) {
            is WorkoutHistoryUiState.Error -> refresh()
            is WorkoutHistoryUiState.ErrorLoadingMore -> {
                _uiState.value = WorkoutHistoryUiState.LoadingMore(state.data, state.page)
                job = viewModelScope.launch { loadPage(state.page, state.data) }
            }
            else -> Unit
        }
    }

    private suspend fun loadFirst() {
        when (val result = repository.getWorkoutHistory(0, PAGE_SIZE)) {
            is WorkoutRepositoryResult.Failure -> _uiState.value =
                WorkoutHistoryUiState.Error(WorkoutHistoryData(), result.error.toWorkoutUiError())
            is WorkoutRepositoryResult.Success -> {
                val page = result.value
                if (page.page != 0) {
                    _uiState.value = invalidFirst()
                    return
                }
                val data = WorkoutHistoryData(
                    items = page.content,
                    nextPage = 1,
                    hasNextPage = !page.last,
                )
                _uiState.value = if (data.items.isEmpty()) WorkoutHistoryUiState.Empty(data)
                else WorkoutHistoryUiState.Content(data)
            }
        }
    }

    private suspend fun loadPage(pageNumber: Int, previous: WorkoutHistoryData) {
        when (val result = repository.getWorkoutHistory(pageNumber, PAGE_SIZE)) {
            is WorkoutRepositoryResult.Failure -> _uiState.value = WorkoutHistoryUiState.ErrorLoadingMore(
                previous, pageNumber, result.error.toWorkoutUiError(),
            )
            is WorkoutRepositoryResult.Success -> {
                val page = result.value
                if (page.page != pageNumber) {
                    _uiState.value = WorkoutHistoryUiState.ErrorLoadingMore(
                        previous, pageNumber, WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
                    )
                    return
                }
                val merged = (previous.items + page.content).distinctBy { it.id }
                _uiState.value = WorkoutHistoryUiState.Content(
                    previous.copy(items = merged, nextPage = pageNumber + 1, hasNextPage = !page.last),
                )
            }
        }
    }

    private fun invalidFirst() = WorkoutHistoryUiState.Error(
        WorkoutHistoryData(), WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
    )

    private companion object { const val PAGE_SIZE = 20 }
}

class WorkoutHistoryViewModelFactory(
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(WorkoutHistoryViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WorkoutHistoryViewModel(repository) as T
    }
}

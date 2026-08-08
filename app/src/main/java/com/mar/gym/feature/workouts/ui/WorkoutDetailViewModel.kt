package com.mar.gym.feature.workouts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState
    data class Content(val workout: WorkoutDetail) : WorkoutDetailUiState
    data class Error(val error: WorkoutUiError) : WorkoutDetailUiState
}

class WorkoutDetailViewModel(
    private val workoutId: String,
    private val repository: WorkoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WorkoutDetailUiState>(WorkoutDetailUiState.Loading)
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = WorkoutDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getWorkout(workoutId)) {
                is WorkoutRepositoryResult.Failure -> WorkoutDetailUiState.Error(result.error.toWorkoutUiError())
                is WorkoutRepositoryResult.Success -> if (result.value.detail.status == WorkoutStatus.Completed) {
                    WorkoutDetailUiState.Content(result.value.detail)
                } else WorkoutDetailUiState.Error(WorkoutUiError(WorkoutUiErrorKind.InvalidResponse))
            }
        }
    }
}

class WorkoutDetailViewModelFactory(
    private val workoutId: String,
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WorkoutDetailViewModel(workoutId, repository) as T
    }
}

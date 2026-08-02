package com.mar.gym.feature.exercises.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data class Content(val detail: ExerciseTemplateDetail) : ExerciseDetailUiState

    data class Error(val error: ExerciseUiError) : ExerciseDetailUiState

    data class NotFound(val correlationId: String?) : ExerciseDetailUiState
}

class ExerciseDetailViewModel(
    private val repository: ExerciseTemplateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var currentId: String? = null

    fun load(exerciseTemplateId: String, force: Boolean = false) {
        if (!force && currentId == exerciseTemplateId &&
            _uiState.value !is ExerciseDetailUiState.Error
        ) {
            return
        }
        currentId = exerciseTemplateId
        loadJob?.cancel()
        _uiState.value = ExerciseDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (
                val result = repository.getExerciseTemplate(exerciseTemplateId)
            ) {
                is ExerciseRepositoryResult.Success -> ExerciseDetailUiState.Content(
                    result.value.copy(instructions = result.value.instructions.sortedBy { it.position })
                )
                is ExerciseRepositoryResult.Failure -> if (result.error.isNotFound()) {
                    ExerciseDetailUiState.NotFound(result.error.correlationId)
                } else {
                    ExerciseDetailUiState.Error(result.error.toDetailUiError())
                }
            }
        }
    }

    fun retry() {
        currentId?.let { load(it, force = true) }
    }

    private fun NetworkFailure.isNotFound(): Boolean = when (this) {
        is NetworkFailure.HttpProblem -> statusCode == 404
        is NetworkFailure.HttpUnknown -> statusCode == 404
        else -> false
    }

    private fun NetworkFailure.toDetailUiError(): ExerciseUiError {
        val kind = when (this) {
            is NetworkFailure.Network -> ExerciseUiErrorKind.Network
            is NetworkFailure.Timeout -> ExerciseUiErrorKind.Timeout
            is NetworkFailure.InvalidResponse -> ExerciseUiErrorKind.InvalidResponse
            is NetworkFailure.HttpProblem -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.HttpUnknown -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.Unexpected -> ExerciseUiErrorKind.Unknown
        }
        return ExerciseUiError(kind, correlationId)
    }
}

class ExerciseDetailViewModelFactory(
    private val repository: ExerciseTemplateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ExerciseDetailViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ExerciseDetailViewModel(repository) as T
    }
}

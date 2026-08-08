package com.mar.gym.feature.exercises.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data class Content(
        val document: ExerciseTemplateDocument,
        val operation: ExerciseDetailOperation? = null,
    ) : ExerciseDetailUiState

    data class Conflict(
        val document: ExerciseTemplateDocument,
        val error: ExerciseUiError,
    ) : ExerciseDetailUiState

    data class Error(
        val error: ExerciseUiError,
        val retainedDocument: ExerciseTemplateDocument? = null,
        val failedOperation: ExerciseDetailOperation? = null,
    ) : ExerciseDetailUiState

    data class NotFound(val correlationId: String?) : ExerciseDetailUiState
}

enum class ExerciseDetailOperation { Archiving, Restoring }

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
                    result.value.copy(
                        detail = result.value.detail.copy(
                            instructions = result.value.detail.instructions.sortedBy { it.position }
                        )
                    )
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
        val state = _uiState.value
        if (state is ExerciseDetailUiState.Error &&
            state.retainedDocument != null && state.failedOperation != null
        ) {
            _uiState.value = ExerciseDetailUiState.Content(state.retainedDocument)
            mutate(state.failedOperation)
        } else {
            currentId?.let { load(it, force = true) }
        }
    }

    fun reload() = currentId?.let { load(it, force = true) }

    fun archive() = mutate(ExerciseDetailOperation.Archiving)

    fun restore() = mutate(ExerciseDetailOperation.Restoring)

    private fun mutate(operation: ExerciseDetailOperation) {
        val content = when (val state = _uiState.value) {
            is ExerciseDetailUiState.Content -> state
            is ExerciseDetailUiState.Conflict -> ExerciseDetailUiState.Content(state.document)
            is ExerciseDetailUiState.Error -> state.retainedDocument?.let {
                ExerciseDetailUiState.Content(it)
            } ?: return
            else -> return
        }
        val document = content.document
        val detail = document.detail
        if (detail.source != ExerciseTemplateSource.Custom) return
        if (operation == ExerciseDetailOperation.Archiving && detail.archived) return
        if (operation == ExerciseDetailOperation.Restoring && !detail.archived) return
        _uiState.value = content.copy(operation = operation)
        viewModelScope.launch {
            val result = when (operation) {
                ExerciseDetailOperation.Archiving -> repository.archiveCustomExercise(
                    detail.id,
                    document.etag,
                )
                ExerciseDetailOperation.Restoring -> repository.restoreCustomExercise(
                    detail.id,
                    document.etag,
                )
            }
            _uiState.value = when (result) {
                is ExerciseRepositoryResult.Success -> ExerciseDetailUiState.Content(result.value)
                is ExerciseRepositoryResult.Failure -> {
                    val error = result.error.toDetailUiError()
                    if (error.kind == ExerciseUiErrorKind.Conflict) {
                        ExerciseDetailUiState.Conflict(document, error)
                    } else {
                        ExerciseDetailUiState.Error(error, document, operation)
                    }
                }
            }
        }
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
                statusCode == 403 -> ExerciseUiErrorKind.Forbidden
                statusCode == 404 -> ExerciseUiErrorKind.NotFound
                problem.errorCode == "EXERCISE_TEMPLATE_VERSION_CONFLICT" ->
                    ExerciseUiErrorKind.Conflict
                problem.errorCode == "EXERCISE_TEMPLATE_NAME_CONFLICT" ->
                    ExerciseUiErrorKind.NameConflict
                problem.errorCode == "EXERCISE_TEMPLATE_ARCHIVED" ->
                    ExerciseUiErrorKind.Archived
                problem.errorCode == "EXERCISE_TEMPLATE_READ_ONLY" ->
                    ExerciseUiErrorKind.Forbidden
                statusCode == 412 -> ExerciseUiErrorKind.Conflict
                statusCode == 400 || statusCode == 422 -> ExerciseUiErrorKind.Validation
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.HttpUnknown -> when {
                statusCode == 401 -> ExerciseUiErrorKind.Unauthorized
                statusCode == 403 -> ExerciseUiErrorKind.Forbidden
                statusCode == 404 -> ExerciseUiErrorKind.NotFound
                statusCode == 412 -> ExerciseUiErrorKind.Conflict
                statusCode == 400 || statusCode == 422 -> ExerciseUiErrorKind.Validation
                statusCode >= 500 -> ExerciseUiErrorKind.Server
                else -> ExerciseUiErrorKind.Unknown
            }
            is NetworkFailure.Unexpected -> ExerciseUiErrorKind.Unknown
        }
        val fields = (this as? NetworkFailure.HttpProblem)?.problem?.fieldErrors
            ?.let(::parseExerciseFieldErrors).orEmpty()
        return ExerciseUiError(kind, correlationId, fields)
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

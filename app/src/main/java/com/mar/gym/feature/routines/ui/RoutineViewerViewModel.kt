package com.mar.gym.feature.routines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.RoutineDocument
import com.mar.gym.feature.routines.model.RoutineEtag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutineViewerViewModel(
    private val routineId: String,
    private val repository: RoutineRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutineViewerUiState>(RoutineViewerUiState.Loading)
    val uiState: StateFlow<RoutineViewerUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RoutineViewerEffect>()
    val effects: SharedFlow<RoutineViewerEffect> = _effects.asSharedFlow()
    private var retryAction: (() -> Unit)? = null

    init { load() }

    fun retry() = retryAction?.invoke()

    fun refresh() = load()

    fun archive() = mutate { id, etag -> repository.archive(id, etag) }
    fun restore() = mutate { id, etag -> repository.restore(id, etag) }
    fun duplicate() = mutate { id, etag -> repository.duplicate(id, etag) }

    fun delete() {
        val current = _uiState.value as? RoutineViewerUiState.Content ?: return
        if (current.busy) return
        _uiState.value = current.copy(busy = true, operationError = null)
        viewModelScope.launch {
            when (val result = repository.delete(current.document.detail.id, current.document.etag)) {
                is RoutineRepositoryResult.Success -> _effects.emit(RoutineViewerEffect.Deleted)
                is RoutineRepositoryResult.Failure -> {
                    val error = result.error.toRoutineUiError()
                    if (error.kind == RoutineUiErrorKind.NotFound) {
                        _effects.emit(RoutineViewerEffect.Unavailable)
                    } else {
                        _uiState.value = current.copy(operationError = error)
                    }
                }
            }
        }
    }

    private fun load() {
        retryAction = { load() }
        _uiState.value = RoutineViewerUiState.Loading
        viewModelScope.launch {
            when (val result = repository.detail(routineId)) {
                is RoutineRepositoryResult.Failure -> {
                    _uiState.value = RoutineViewerUiState.Error(result.error.toRoutineUiError())
                }
                is RoutineRepositoryResult.Success -> {
                    retryAction = null
                    _uiState.value = RoutineViewerUiState.Content(result.value)
                }
            }
        }
    }

    private fun mutate(
        request: suspend (String, RoutineEtag) -> RoutineRepositoryResult<RoutineDocument>,
    ) {
        val current = _uiState.value as? RoutineViewerUiState.Content ?: return
        if (current.busy) return
        _uiState.value = current.copy(busy = true, operationError = null)
        viewModelScope.launch {
            when (val result = request(current.document.detail.id, current.document.etag)) {
                is RoutineRepositoryResult.Failure -> {
                    _uiState.value = RoutineViewerUiState.Content(
                        current.document,
                        busy = false,
                        operationError = result.error.toRoutineUiError(),
                    )
                }
                is RoutineRepositoryResult.Success -> {
                    _uiState.value = RoutineViewerUiState.Content(result.value)
                    if (result.value.detail.id != routineId) {
                        _effects.emit(RoutineViewerEffect.OpenRoutine(result.value.detail.id))
                    }
                }
            }
        }
    }
}

class RoutineViewerViewModelFactory(
    private val routineId: String,
    private val repository: RoutineRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(RoutineViewerViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return RoutineViewerViewModel(routineId, repository) as T
    }
}

package com.mar.gym.feature.routines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.SharedRoutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SharedRoutineUiState {
    data object Loading : SharedRoutineUiState
    data class Content(val routine: SharedRoutine) : SharedRoutineUiState
    data class Error(val error: RoutineUiError) : SharedRoutineUiState
}

class SharedRoutineViewModel(
    private val shareId: String,
    private val repository: RoutineRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SharedRoutineUiState>(SharedRoutineUiState.Loading)
    val uiState: StateFlow<SharedRoutineUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        _uiState.value = SharedRoutineUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.sharedDetail(shareId)) {
                is RoutineRepositoryResult.Success -> SharedRoutineUiState.Content(result.value)
                is RoutineRepositoryResult.Failure -> SharedRoutineUiState.Error(result.error.toRoutineUiError())
            }
        }
    }
}

class SharedRoutineViewModelFactory(
    private val shareId: String,
    private val repository: RoutineRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SharedRoutineViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SharedRoutineViewModel(shareId, repository) as T
    }
}

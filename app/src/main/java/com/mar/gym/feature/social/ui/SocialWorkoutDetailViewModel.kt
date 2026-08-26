package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SocialWorkoutDetailUiState {
    data object Loading : SocialWorkoutDetailUiState
    data class Content(val workout: SocialWorkoutDetail) : SocialWorkoutDetailUiState
    data class Error(val error: SocialUiError) : SocialWorkoutDetailUiState
}

class SocialWorkoutDetailViewModel(
    private val workoutId: String,
    private val repository: SocialFeedRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SocialWorkoutDetailUiState>(SocialWorkoutDetailUiState.Loading)
    val uiState: StateFlow<SocialWorkoutDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _uiState.value = SocialWorkoutDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.workoutDetail(workoutId)) {
                is SocialResult.Failure -> SocialWorkoutDetailUiState.Error(result.error.toSocialUiError())
                is SocialResult.Success -> SocialWorkoutDetailUiState.Content(result.value)
            }
        }
    }
}

class SocialWorkoutDetailViewModelFactory(
    private val workoutId: String,
    private val repository: SocialFeedRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SocialWorkoutDetailViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SocialWorkoutDetailViewModel(workoutId, repository) as T
    }
}

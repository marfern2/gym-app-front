package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import com.mar.gym.feature.workouts.ui.WorkoutUiError
import com.mar.gym.feature.workouts.ui.WorkoutUiErrorKind
import com.mar.gym.feature.workouts.ui.toWorkoutUiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SocialWorkoutDetailUiState {
    data object Loading : SocialWorkoutDetailUiState
    data class Content(
        val workout: SocialWorkoutDetail,
        val ownerDocument: WorkoutDocument? = null,
        val visibilityChanging: Boolean = false,
        val visibilityError: WorkoutUiError? = null,
        val visibilityChangeVersion: Long = 0,
    ) : SocialWorkoutDetailUiState
    data class Error(val error: SocialUiError) : SocialWorkoutDetailUiState
}

class SocialWorkoutDetailViewModel(
    private val workoutId: String,
    private val repository: SocialFeedRepository,
    private val currentUserId: String? = null,
    private val workoutRepository: WorkoutRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SocialWorkoutDetailUiState>(SocialWorkoutDetailUiState.Loading)
    val uiState: StateFlow<SocialWorkoutDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun retry() = load()

    fun updateVisibility(visibility: WorkoutVisibility) {
        val current = _uiState.value as? SocialWorkoutDetailUiState.Content ?: return
        val document = current.ownerDocument ?: return
        val ownerRepository = workoutRepository ?: return
        if (current.visibilityChanging || document.detail.socialVisibility == visibility) return
        _uiState.value = current.copy(visibilityChanging = true, visibilityError = null)
        viewModelScope.launch {
            when (val result = ownerRepository.updateWorkoutVisibility(workoutId, visibility, document.etag)) {
                is WorkoutRepositoryResult.Failure -> _uiState.value = current.copy(
                    visibilityChanging = false,
                    visibilityError = result.error.toWorkoutUiError(),
                )
                is WorkoutRepositoryResult.Success -> {
                    val updated = result.value
                    _uiState.value = if (
                        updated.detail.id == workoutId &&
                        updated.detail.status == WorkoutStatus.Completed &&
                        updated.detail.socialVisibility == visibility
                    ) {
                        current.copy(
                            workout = current.workout.copy(socialVisibility = visibility),
                            ownerDocument = updated,
                            visibilityChanging = false,
                            visibilityError = null,
                            visibilityChangeVersion = current.visibilityChangeVersion + 1,
                        )
                    } else {
                        current.copy(
                            visibilityChanging = false,
                            visibilityError = WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
                        )
                    }
                }
            }
        }
    }

    fun reloadOwnerDocument() {
        val current = _uiState.value as? SocialWorkoutDetailUiState.Content ?: return
        val ownerRepository = workoutRepository ?: return
        if (current.ownerDocument == null) return
        _uiState.value = current.copy(visibilityChanging = true, visibilityError = null)
        viewModelScope.launch {
            _uiState.value = when (val result = ownerRepository.getWorkout(workoutId)) {
                is WorkoutRepositoryResult.Failure -> current.copy(
                    visibilityChanging = false,
                    visibilityError = result.error.toWorkoutUiError(),
                )
                is WorkoutRepositoryResult.Success -> canonicalOwnerContent(current, result.value)
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = SocialWorkoutDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.workoutDetail(workoutId)) {
                is SocialResult.Failure -> SocialWorkoutDetailUiState.Error(result.error.toSocialUiError())
                is SocialResult.Success -> {
                    val content = SocialWorkoutDetailUiState.Content(result.value)
                    if (result.value.author.userId != currentUserId || workoutRepository == null) {
                        content
                    } else when (val owner = workoutRepository.getWorkout(workoutId)) {
                        is WorkoutRepositoryResult.Failure -> content.copy(
                            visibilityError = owner.error.toWorkoutUiError(),
                        )
                        is WorkoutRepositoryResult.Success -> canonicalOwnerContent(content, owner.value)
                    }
                }
            }
        }
    }

    private fun canonicalOwnerContent(
        current: SocialWorkoutDetailUiState.Content,
        document: WorkoutDocument,
    ): SocialWorkoutDetailUiState.Content = if (
        document.detail.id == workoutId && document.detail.status == WorkoutStatus.Completed
    ) {
        current.copy(
            workout = current.workout.copy(socialVisibility = document.detail.socialVisibility),
            ownerDocument = document,
            visibilityChanging = false,
            visibilityError = null,
        )
    } else {
        current.copy(
            visibilityChanging = false,
            visibilityError = WorkoutUiError(WorkoutUiErrorKind.InvalidResponse),
        )
    }
}

class SocialWorkoutDetailViewModelFactory(
    private val workoutId: String,
    private val repository: SocialFeedRepository,
    private val currentUserId: String? = null,
    private val workoutRepository: WorkoutRepository? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SocialWorkoutDetailViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SocialWorkoutDetailViewModel(workoutId, repository, currentUserId, workoutRepository) as T
    }
}

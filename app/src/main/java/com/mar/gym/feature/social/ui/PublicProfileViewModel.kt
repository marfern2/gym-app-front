package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialModerationRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Content(
        val profile: PublicProfile,
        val isOwnProfile: Boolean,
        val followInFlight: Boolean = false,
        val blockConfirmationOpen: Boolean = false,
        val blockInFlight: Boolean = false,
        val actionError: SocialUiError? = null,
        val workouts: List<SocialWorkoutSummary> = emptyList(),
        val workoutsLoading: Boolean = true,
        val workoutsLoadingMore: Boolean = false,
        val workoutsError: SocialUiError? = null,
        val workoutsLoadMoreError: SocialUiError? = null,
        val workoutsNextCursor: String? = null,
        val workoutsHasMore: Boolean = false,
    ) : PublicProfileUiState
    data class Error(val error: SocialUiError) : PublicProfileUiState
}

class PublicProfileViewModel(
    private val username: String,
    private val currentUserId: String,
    private val repository: SocialRepository,
    private val feedRepository: SocialFeedRepository,
    private val moderationRepository: SocialModerationRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()
    private val effectChannel = Channel<PublicProfileEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init { load() }

    private var profileJob: Job? = null
    private var workoutsGeneration = 0

    fun retry() {
        when (val current = _uiState.value) {
            is PublicProfileUiState.Content -> if (current.workoutsLoadMoreError != null) {
                loadMoreWorkouts()
            } else if (current.workoutsError != null) {
                loadWorkouts(clearCurrent = current.workouts.isEmpty())
            }
            else -> load()
        }
    }

    fun loadMoreWorkouts() {
        val current = _uiState.value as? PublicProfileUiState.Content ?: return
        if (current.workoutsLoading || current.workoutsLoadingMore || !current.workoutsHasMore) return
        val cursor = current.workoutsNextCursor ?: return
        val generation = workoutsGeneration
        _uiState.value = current.copy(workoutsLoadingMore = true, workoutsLoadMoreError = null)
        viewModelScope.launch {
            when (val result = feedRepository.userWorkouts(username, cursor, WORKOUT_PAGE_SIZE)) {
                is SocialResult.Failure -> if (generation == workoutsGeneration) updateContent { latest ->
                    latest.copy(
                        workoutsLoadingMore = false,
                        workoutsLoadMoreError = result.error.toSocialUiError(),
                    )
                }
                is SocialResult.Success -> if (generation == workoutsGeneration) updateContent { latest ->
                    val ids = latest.workouts.asSequence().map { it.workoutId }.toHashSet()
                    latest.copy(
                        workouts = latest.workouts + result.value.content.filter { ids.add(it.workoutId) },
                        workoutsLoadingMore = false,
                        workoutsLoadMoreError = null,
                        workoutsNextCursor = result.value.nextCursor,
                        workoutsHasMore = result.value.hasMore,
                    )
                }
            }
        }
    }

    fun toggleFollow() {
        val current = _uiState.value as? PublicProfileUiState.Content ?: return
        if (current.isOwnProfile || current.followInFlight) return
        val wasFollowing = current.profile.isFollowing
        val optimistic = current.profile.copy(
            isFollowing = !wasFollowing,
            followersCount = if (wasFollowing) {
                (current.profile.followersCount - 1).coerceAtLeast(0)
            } else {
                current.profile.followersCount + 1
            },
        )
        _uiState.value = current.copy(profile = optimistic, followInFlight = true, actionError = null)
        viewModelScope.launch {
            val result = if (wasFollowing) repository.unfollow(username) else repository.follow(username)
            val latest = _uiState.value as? PublicProfileUiState.Content ?: return@launch
            _uiState.value = when (result) {
                is SocialResult.Success -> latest.copy(followInFlight = false)
                is SocialResult.Failure -> latest.copy(
                    profile = current.profile,
                    followInFlight = false,
                    actionError = result.error.toSocialUiError(),
                )
            }
        }
    }

    fun requestBlock() {
        val current = _uiState.value as? PublicProfileUiState.Content ?: return
        if (current.isOwnProfile || current.blockInFlight) return
        _uiState.value = current.copy(blockConfirmationOpen = true, actionError = null)
    }

    fun cancelBlock() {
        updateContent { if (it.blockInFlight) it else it.copy(blockConfirmationOpen = false) }
    }

    fun confirmBlock() {
        val current = _uiState.value as? PublicProfileUiState.Content ?: return
        val moderation = moderationRepository ?: return
        if (current.isOwnProfile || current.blockInFlight || !current.blockConfirmationOpen) return
        _uiState.value = current.copy(blockInFlight = true, actionError = null)
        viewModelScope.launch {
            when (val result = moderation.block(current.profile.username)) {
                is SocialResult.Success -> {
                    val latest = _uiState.value as? PublicProfileUiState.Content ?: return@launch
                    _uiState.value = latest.copy(
                        profile = latest.profile.copy(isFollowing = false),
                        followInFlight = false,
                        blockInFlight = false,
                        blockConfirmationOpen = false,
                    )
                    effectChannel.send(PublicProfileEffect.Blocked(current.profile.userId, current.profile.username))
                }
                is SocialResult.Failure -> {
                    val error = if (result.error.isHttpNotFound()) SocialUiError.NotFound
                    else result.error.toSocialUiError()
                    if (error == SocialUiError.NotFound) {
                        _uiState.value = PublicProfileUiState.Error(SocialUiError.NotFound)
                    } else updateContent {
                        it.copy(blockInFlight = false, actionError = error)
                    }
                }
            }
        }
    }

    private fun load() {
        profileJob?.cancel()
        _uiState.value = PublicProfileUiState.Loading
        profileJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.profile(username)) {
                is SocialResult.Failure -> PublicProfileUiState.Error(result.error.toSocialUiError())
                is SocialResult.Success -> PublicProfileUiState.Content(
                    profile = result.value,
                    isOwnProfile = result.value.userId == currentUserId,
                )
            }
            if (_uiState.value is PublicProfileUiState.Content) loadWorkouts(clearCurrent = true)
        }
    }

    private fun loadWorkouts(clearCurrent: Boolean) {
        val current = _uiState.value as? PublicProfileUiState.Content ?: return
        val generation = ++workoutsGeneration
        _uiState.value = current.copy(
            workouts = if (clearCurrent) emptyList() else current.workouts,
            workoutsLoading = true,
            workoutsLoadingMore = false,
            workoutsError = null,
            workoutsLoadMoreError = null,
        )
        viewModelScope.launch {
            when (val result = feedRepository.userWorkouts(username, null, WORKOUT_PAGE_SIZE)) {
                is SocialResult.Failure -> if (generation == workoutsGeneration) updateContent { latest ->
                    latest.copy(
                        workoutsLoading = false,
                        workoutsError = result.error.toSocialUiError(),
                    )
                }
                is SocialResult.Success -> if (generation == workoutsGeneration) updateContent { latest ->
                    latest.copy(
                        workouts = result.value.content.distinctBy { it.workoutId },
                        workoutsLoading = false,
                        workoutsError = null,
                        workoutsNextCursor = result.value.nextCursor,
                        workoutsHasMore = result.value.hasMore,
                    )
                }
            }
        }
    }

    private inline fun updateContent(transform: (PublicProfileUiState.Content) -> PublicProfileUiState.Content) {
        val latest = _uiState.value as? PublicProfileUiState.Content ?: return
        _uiState.value = transform(latest)
    }

    private companion object {
        const val WORKOUT_PAGE_SIZE = 20
    }
}

class PublicProfileViewModelFactory(
    private val username: String,
    private val currentUserId: String,
    private val repository: SocialRepository,
    private val feedRepository: SocialFeedRepository,
    private val moderationRepository: SocialModerationRepository? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(PublicProfileViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return PublicProfileViewModel(username, currentUserId, repository, feedRepository, moderationRepository) as T
    }
}

private fun NetworkFailure.isHttpNotFound(): Boolean = when (this) {
    is NetworkFailure.HttpProblem -> statusCode == 404
    is NetworkFailure.HttpUnknown -> statusCode == 404
    else -> false
}

sealed interface PublicProfileEffect {
    data class Blocked(val userId: String, val username: String) : PublicProfileEffect
}

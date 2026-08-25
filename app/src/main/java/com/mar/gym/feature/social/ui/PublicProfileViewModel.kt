package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState
    data class Content(
        val profile: PublicProfile,
        val isOwnProfile: Boolean,
        val followInFlight: Boolean = false,
        val actionError: SocialUiError? = null,
    ) : PublicProfileUiState
    data class Error(val error: SocialUiError) : PublicProfileUiState
}

class PublicProfileViewModel(
    private val username: String,
    private val currentUserId: String,
    private val repository: SocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

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

    private fun load() {
        _uiState.value = PublicProfileUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.profile(username)) {
                is SocialResult.Failure -> PublicProfileUiState.Error(result.error.toSocialUiError())
                is SocialResult.Success -> PublicProfileUiState.Content(
                    profile = result.value,
                    isOwnProfile = result.value.userId == currentUserId,
                )
            }
        }
    }
}

class PublicProfileViewModelFactory(
    private val username: String,
    private val currentUserId: String,
    private val repository: SocialRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(PublicProfileViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return PublicProfileViewModel(username, currentUserId, repository) as T
    }
}

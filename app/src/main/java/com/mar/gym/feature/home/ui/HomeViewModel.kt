package com.mar.gym.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.ui.SocialUiError
import com.mar.gym.feature.social.ui.toSocialUiError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val workouts: List<SocialWorkoutSummary> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val feedError: SocialUiError? = null,
    val loadMoreError: SocialUiError? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val suggestions: List<SuggestedAthlete> = emptyList(),
    val suggestionsLoading: Boolean = true,
    val suggestionsError: SocialUiError? = null,
    val followingUsernames: Set<String> = emptySet(),
    val suggestionActionErrors: Map<String, SocialUiError> = emptyMap(),
)

class HomeViewModel(
    private val feedRepository: SocialFeedRepository,
    private val socialRepository: SocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var feedJob: Job? = null
    private var suggestionsJob: Job? = null
    private var feedGeneration = 0

    init {
        loadFeed(refresh = false)
        loadSuggestions(clearCurrent = true)
    }

    fun retry() = loadFeed(refresh = false)

    fun retrySuggestions() = loadSuggestions(clearCurrent = false)

    fun refresh() {
        loadFeed(refresh = true)
        loadSuggestions(clearCurrent = false)
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.initialLoading || current.refreshing || current.loadingMore || !current.hasMore) return
        val cursor = current.nextCursor ?: return
        val generation = feedGeneration
        _uiState.value = current.copy(loadingMore = true, loadMoreError = null)
        viewModelScope.launch {
            when (val result = feedRepository.feed(cursor, FEED_PAGE_SIZE)) {
                is SocialResult.Failure -> if (generation == feedGeneration) {
                    _uiState.value = _uiState.value.copy(
                        loadingMore = false,
                        loadMoreError = result.error.toSocialUiError(),
                    )
                }
                is SocialResult.Success -> if (generation == feedGeneration) {
                    val latest = _uiState.value
                    val knownIds = latest.workouts.asSequence().map { it.workoutId }.toHashSet()
                    _uiState.value = latest.copy(
                        workouts = latest.workouts + result.value.content.filter { knownIds.add(it.workoutId) },
                        loadingMore = false,
                        loadMoreError = null,
                        nextCursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                    )
                }
            }
        }
    }

    fun follow(username: String) {
        val current = _uiState.value
        val athlete = current.suggestions.firstOrNull { it.username == username } ?: return
        if (username in current.followingUsernames || athlete.isFollowing) return
        _uiState.value = current.copy(
            suggestions = current.suggestions.map {
                if (it.username == username) it.copy(isFollowing = true) else it
            },
            followingUsernames = current.followingUsernames + username,
            suggestionActionErrors = current.suggestionActionErrors - username,
        )
        viewModelScope.launch {
            when (val result = socialRepository.follow(username)) {
                is SocialResult.Success -> {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        suggestions = latest.suggestions.filterNot { it.username == username },
                        followingUsernames = latest.followingUsernames - username,
                    )
                }
                is SocialResult.Failure -> {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        suggestions = latest.suggestions.map {
                            if (it.username == username) athlete else it
                        },
                        followingUsernames = latest.followingUsernames - username,
                        suggestionActionErrors = latest.suggestionActionErrors +
                            (username to result.error.toSocialUiError()),
                    )
                }
            }
        }
    }

    private fun loadFeed(refresh: Boolean) {
        feedJob?.cancel()
        val generation = ++feedGeneration
        val current = _uiState.value
        _uiState.value = if (refresh && current.workouts.isNotEmpty()) {
            current.copy(refreshing = true, feedError = null, loadMoreError = null)
        } else {
            current.copy(
                initialLoading = true,
                refreshing = false,
                loadingMore = false,
                feedError = null,
                loadMoreError = null,
            )
        }
        feedJob = viewModelScope.launch {
            when (val result = feedRepository.feed(cursor = null, size = FEED_PAGE_SIZE)) {
                is SocialResult.Failure -> if (generation == feedGeneration) {
                    _uiState.value = _uiState.value.copy(
                        initialLoading = false,
                        refreshing = false,
                        feedError = result.error.toSocialUiError(),
                    )
                }
                is SocialResult.Success -> if (generation == feedGeneration) {
                    _uiState.value = _uiState.value.copy(
                        workouts = result.value.content.distinctBy { it.workoutId },
                        initialLoading = false,
                        refreshing = false,
                        feedError = null,
                        nextCursor = result.value.nextCursor,
                        hasMore = result.value.hasMore,
                    )
                }
            }
        }
    }

    private fun loadSuggestions(clearCurrent: Boolean) {
        suggestionsJob?.cancel()
        val current = _uiState.value
        _uiState.value = current.copy(
            suggestions = if (clearCurrent) emptyList() else current.suggestions,
            suggestionsLoading = true,
            suggestionsError = null,
        )
        suggestionsJob = viewModelScope.launch {
            when (val result = feedRepository.suggestions(page = 0, size = SUGGESTIONS_PAGE_SIZE)) {
                is SocialResult.Failure -> _uiState.value = _uiState.value.copy(
                    suggestionsLoading = false,
                    suggestionsError = result.error.toSocialUiError(),
                )
                is SocialResult.Success -> _uiState.value = _uiState.value.copy(
                    suggestions = result.value.content.filterNot(SuggestedAthlete::isFollowing)
                        .distinctBy(SuggestedAthlete::userId),
                    suggestionsLoading = false,
                    suggestionsError = null,
                )
            }
        }
    }

    private companion object {
        const val FEED_PAGE_SIZE = 20
        const val SUGGESTIONS_PAGE_SIZE = 20
    }
}

class HomeViewModelFactory(
    private val feedRepository: SocialFeedRepository,
    private val socialRepository: SocialRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(feedRepository, socialRepository) as T
    }
}

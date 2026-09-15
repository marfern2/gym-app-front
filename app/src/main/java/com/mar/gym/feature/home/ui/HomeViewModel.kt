package com.mar.gym.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SocialWorkoutSummary
import com.mar.gym.feature.social.model.SuggestedAthlete
import com.mar.gym.feature.social.ui.SocialUiError
import com.mar.gym.feature.social.ui.toSocialUiError
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HomeFeedMode { Home, Discover }

data class FeedUiState(
    val workouts: List<SocialWorkoutSummary> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val feedError: SocialUiError? = null,
    val loadMoreError: SocialUiError? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

data class HomeUiState(
    val selectedMode: HomeFeedMode = HomeFeedMode.Home,
    val home: FeedUiState = FeedUiState(initialLoading = true),
    val discover: FeedUiState = FeedUiState(),
    val suggestions: List<SuggestedAthlete> = emptyList(),
    val suggestionsLoading: Boolean = true,
    val suggestionsError: SocialUiError? = null,
    val followingUsernames: Set<String> = emptySet(),
    val suggestionActionErrors: Map<String, SocialUiError> = emptyMap(),
    val discoverFollowingUsernames: Set<String> = emptySet(),
    val discoverFollowErrors: Map<String, SocialUiError> = emptyMap(),
) {
    val activeFeed: FeedUiState
        get() = if (selectedMode == HomeFeedMode.Home) home else discover
}

class HomeViewModel(
    private val feedRepository: SocialFeedRepository,
    private val socialRepository: SocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val feedJobs = mutableMapOf<HomeFeedMode, Job>()
    private val feedGenerations = mutableMapOf(
        HomeFeedMode.Home to 0,
        HomeFeedMode.Discover to 0,
    )
    private val attemptedModes = mutableSetOf(HomeFeedMode.Home)
    private var suggestionsJob: Job? = null

    init {
        loadFeed(HomeFeedMode.Home, refresh = false)
        loadSuggestions(clearCurrent = true)
    }

    fun selectMode(mode: HomeFeedMode) {
        if (_uiState.value.selectedMode == mode) return
        _uiState.value = _uiState.value.copy(selectedMode = mode)
        if (attemptedModes.add(mode)) loadFeed(mode, refresh = false)
    }

    fun retry() {
        val mode = _uiState.value.selectedMode
        loadFeed(mode, refresh = feedState(mode).workouts.isNotEmpty())
    }

    fun retrySuggestions() = loadSuggestions(clearCurrent = false)

    fun refresh() {
        val mode = _uiState.value.selectedMode
        attemptedModes += mode
        loadFeed(mode, refresh = true)
        if (mode == HomeFeedMode.Home) loadSuggestions(clearCurrent = false)
    }

    fun refreshAfterOwnVisibilityChange(workoutId: String, visibility: WorkoutVisibility) {
        if (visibility == WorkoutVisibility.Private) {
            updateFeed(HomeFeedMode.Home) { feed ->
                feed.copy(workouts = feed.workouts.filterNot { it.workoutId == workoutId })
            }
            updateFeed(HomeFeedMode.Discover) { feed ->
                feed.copy(workouts = feed.workouts.filterNot { it.workoutId == workoutId })
            }
        }
        attemptedModes.forEach { mode -> loadFeed(mode, refresh = true) }
    }

    fun onUserBlocked(userId: String, username: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            home = current.home.copy(workouts = current.home.workouts.filterNot { it.author.userId == userId }),
            discover = current.discover.copy(workouts = current.discover.workouts.filterNot { it.author.userId == userId }),
            suggestions = current.suggestions.filterNot { it.userId == userId || it.username == username },
            followingUsernames = current.followingUsernames - username,
            suggestionActionErrors = current.suggestionActionErrors - username,
            discoverFollowingUsernames = current.discoverFollowingUsernames - username,
            discoverFollowErrors = current.discoverFollowErrors - username,
        )
        attemptedModes.forEach { loadFeed(it, refresh = true) }
        loadSuggestions(clearCurrent = false)
    }

    fun loadMore() {
        val mode = _uiState.value.selectedMode
        val current = feedState(mode)
        if (current.initialLoading || current.refreshing || current.loadingMore || !current.hasMore) return
        val cursor = current.nextCursor ?: return
        val generation = feedGenerations.getValue(mode)
        updateFeed(mode) { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            when (val result = requestFeed(mode, cursor)) {
                is SocialResult.Failure -> if (generation == feedGenerations.getValue(mode)) {
                    updateFeed(mode) {
                        it.copy(loadingMore = false, loadMoreError = result.error.toSocialUiError())
                    }
                }
                is SocialResult.Success -> if (generation == feedGenerations.getValue(mode)) {
                    appendPage(mode, result.value)
                }
            }
        }
    }

    fun follow(username: String) {
        if (_uiState.value.selectedMode == HomeFeedMode.Discover) {
            followFromDiscover(username)
        } else {
            followSuggestion(username)
        }
    }

    private fun followSuggestion(username: String) {
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
                    loadFeed(HomeFeedMode.Home, refresh = true)
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

    private fun followFromDiscover(username: String) {
        val current = _uiState.value
        val author = current.discover.workouts.asSequence()
            .map(SocialWorkoutSummary::author)
            .firstOrNull { it.username == username } ?: return
        if (username in current.discoverFollowingUsernames) return
        _uiState.value = current.copy(
            discoverFollowingUsernames = current.discoverFollowingUsernames + username,
            discoverFollowErrors = current.discoverFollowErrors - username,
        )
        viewModelScope.launch {
            when (val result = socialRepository.follow(username)) {
                is SocialResult.Success -> {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        discover = latest.discover.copy(
                            workouts = latest.discover.workouts.filterNot { it.author.userId == author.userId },
                        ),
                        discoverFollowingUsernames = latest.discoverFollowingUsernames - username,
                        discoverFollowErrors = latest.discoverFollowErrors - username,
                    )
                }
                is SocialResult.Failure -> {
                    val latest = _uiState.value
                    _uiState.value = latest.copy(
                        discoverFollowingUsernames = latest.discoverFollowingUsernames - username,
                        discoverFollowErrors = latest.discoverFollowErrors +
                            (username to result.error.toSocialUiError()),
                    )
                }
            }
        }
    }

    private fun loadFeed(mode: HomeFeedMode, refresh: Boolean) {
        feedJobs[mode]?.cancel()
        val generation = feedGenerations.getValue(mode) + 1
        feedGenerations[mode] = generation
        val current = feedState(mode)
        updateFeed(mode) {
            if (refresh && current.workouts.isNotEmpty()) {
                current.copy(refreshing = true, loadingMore = false, feedError = null, loadMoreError = null)
            } else {
                current.copy(
                    initialLoading = true,
                    refreshing = false,
                    loadingMore = false,
                    feedError = null,
                    loadMoreError = null,
                )
            }
        }
        feedJobs[mode] = viewModelScope.launch {
            when (val result = requestFeed(mode, cursor = null)) {
                is SocialResult.Failure -> if (generation == feedGenerations.getValue(mode)) {
                    updateFeed(mode) {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            feedError = result.error.toSocialUiError(),
                        )
                    }
                }
                is SocialResult.Success -> if (generation == feedGenerations.getValue(mode)) {
                    updateFeed(mode) {
                        it.copy(
                            workouts = result.value.content.distinctBy(SocialWorkoutSummary::workoutId),
                            initialLoading = false,
                            refreshing = false,
                            feedError = null,
                            loadMoreError = null,
                            nextCursor = result.value.nextCursor,
                            hasMore = result.value.hasMore,
                        )
                    }
                }
            }
        }
    }

    private suspend fun requestFeed(
        mode: HomeFeedMode,
        cursor: String?,
    ): SocialResult<SocialWorkoutPage> = when (mode) {
        HomeFeedMode.Home -> feedRepository.feed(cursor, FEED_PAGE_SIZE)
        HomeFeedMode.Discover -> feedRepository.discover(cursor, FEED_PAGE_SIZE)
    }

    private fun appendPage(mode: HomeFeedMode, page: SocialWorkoutPage) {
        updateFeed(mode) { latest ->
            val knownIds = latest.workouts.asSequence().map(SocialWorkoutSummary::workoutId).toHashSet()
            latest.copy(
                workouts = latest.workouts + page.content.filter { knownIds.add(it.workoutId) },
                loadingMore = false,
                loadMoreError = null,
                nextCursor = page.nextCursor,
                hasMore = page.hasMore,
            )
        }
    }

    private fun feedState(mode: HomeFeedMode): FeedUiState = when (mode) {
        HomeFeedMode.Home -> _uiState.value.home
        HomeFeedMode.Discover -> _uiState.value.discover
    }

    private inline fun updateFeed(mode: HomeFeedMode, transform: (FeedUiState) -> FeedUiState) {
        val state = _uiState.value
        _uiState.value = when (mode) {
            HomeFeedMode.Home -> state.copy(home = transform(state.home))
            HomeFeedMode.Discover -> state.copy(discover = transform(state.discover))
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

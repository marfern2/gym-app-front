package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserSearchData(
    val query: String = "",
    val users: List<PublicProfile> = emptyList(),
    val page: Int = -1,
    val hasNextPage: Boolean = false,
)

sealed interface UserSearchUiState {
    val data: UserSearchData
    data class Idle(override val data: UserSearchData = UserSearchData()) : UserSearchUiState
    data class Loading(override val data: UserSearchData) : UserSearchUiState
    data class Content(override val data: UserSearchData) : UserSearchUiState
    data class Empty(override val data: UserSearchData) : UserSearchUiState
    data class Error(override val data: UserSearchData, val error: SocialUiError) : UserSearchUiState
    data class LoadingMore(override val data: UserSearchData, val requestedPage: Int) : UserSearchUiState
    data class ErrorLoadingMore(
        override val data: UserSearchData,
        val requestedPage: Int,
        val error: SocialUiError,
    ) : UserSearchUiState
}

class UserSearchViewModel(
    private val repository: SocialRepository,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UserSearchUiState>(UserSearchUiState.Idle())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L

    fun onQueryChanged(query: String) {
        generation++
        searchJob?.cancel()
        moreJob?.cancel()
        val data = UserSearchData(query = query)
        if (query.normalized() == null) {
            _uiState.value = UserSearchUiState.Idle(data)
            return
        }
        search(debounceMillis, data, generation)
    }

    fun refresh() {
        val data = _uiState.value.data
        if (data.query.normalized() == null) return
        generation++
        searchJob?.cancel()
        moreJob?.cancel()
        search(0, data.copy(users = emptyList(), page = -1, hasNextPage = false), generation)
    }

    fun retry() = when (val state = _uiState.value) {
        is UserSearchUiState.Error -> refresh()
        is UserSearchUiState.ErrorLoadingMore -> loadPage(state.requestedPage)
        else -> Unit
    }

    fun onUserBlocked(userId: String, username: String) {
        val state = _uiState.value
        val updated = state.data.copy(users = state.data.users.filterNot { it.userId == userId || it.username == username })
        _uiState.value = when (state) {
            is UserSearchUiState.Idle -> UserSearchUiState.Idle(updated)
            is UserSearchUiState.Loading -> UserSearchUiState.Loading(updated)
            is UserSearchUiState.Content -> if (updated.users.isEmpty()) UserSearchUiState.Empty(updated) else UserSearchUiState.Content(updated)
            is UserSearchUiState.Empty -> UserSearchUiState.Empty(updated)
            is UserSearchUiState.Error -> UserSearchUiState.Error(updated, state.error)
            is UserSearchUiState.LoadingMore -> UserSearchUiState.LoadingMore(updated, state.requestedPage)
            is UserSearchUiState.ErrorLoadingMore -> UserSearchUiState.ErrorLoadingMore(updated, state.requestedPage, state.error)
        }
        refresh()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state !is UserSearchUiState.Content || !state.data.hasNextPage || moreJob?.isActive == true) return
        loadPage(state.data.page + 1)
    }

    private fun search(delayMillis: Long, data: UserSearchData, requestedGeneration: Long) {
        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val query = data.query.normalized() ?: return@launch
            _uiState.value = UserSearchUiState.Loading(data)
            when (val result = repository.search(query, 0, PAGE_SIZE)) {
                is SocialResult.Failure -> if (requestedGeneration == generation) {
                    _uiState.value = UserSearchUiState.Error(data, result.error.toSocialUiError())
                }
                is SocialResult.Success -> if (requestedGeneration == generation) {
                    val page = result.value
                    if (page.page != 0) {
                        _uiState.value = UserSearchUiState.Error(data, SocialUiError.InvalidResponse)
                    } else {
                        val updated = data.copy(
                            users = page.content.distinctBy(PublicProfile::userId),
                            page = 0,
                            hasNextPage = !page.last,
                        )
                        _uiState.value = if (updated.users.isEmpty()) UserSearchUiState.Empty(updated)
                        else UserSearchUiState.Content(updated)
                    }
                }
            }
        }
    }

    private fun loadPage(page: Int) {
        val requestedGeneration = generation
        val data = _uiState.value.data
        val query = data.query.normalized() ?: return
        _uiState.value = UserSearchUiState.LoadingMore(data, page)
        moreJob = viewModelScope.launch {
            when (val result = repository.search(query, page, PAGE_SIZE)) {
                is SocialResult.Failure -> if (requestedGeneration == generation) {
                    _uiState.value = UserSearchUiState.ErrorLoadingMore(data, page, result.error.toSocialUiError())
                }
                is SocialResult.Success -> if (requestedGeneration == generation) {
                    if (result.value.page != page) {
                        _uiState.value = UserSearchUiState.ErrorLoadingMore(data, page, SocialUiError.InvalidResponse)
                    } else {
                        val ids = data.users.mapTo(mutableSetOf(), PublicProfile::userId)
                        _uiState.value = UserSearchUiState.Content(data.copy(
                            users = data.users + result.value.content.filter { ids.add(it.userId) },
                            page = page,
                            hasNextPage = !result.value.last,
                        ))
                    }
                }
            }
        }
    }

    private fun String.normalized() = trim().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty)

    companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_DEBOUNCE = 400L
    }
}

class UserSearchViewModelFactory(private val repository: SocialRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(UserSearchViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return UserSearchViewModel(repository) as T
    }
}

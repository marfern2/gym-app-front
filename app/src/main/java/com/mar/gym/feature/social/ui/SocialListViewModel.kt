package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SocialListType { Followers, Following }

data class SocialListData(
    val users: List<PublicProfile> = emptyList(),
    val page: Int = -1,
    val hasNextPage: Boolean = false,
)

sealed interface SocialListUiState {
    val data: SocialListData
    data class Loading(override val data: SocialListData = SocialListData()) : SocialListUiState
    data class Content(override val data: SocialListData) : SocialListUiState
    data class Empty(override val data: SocialListData) : SocialListUiState
    data class Error(override val data: SocialListData, val error: SocialUiError) : SocialListUiState
    data class LoadingMore(override val data: SocialListData, val requestedPage: Int) : SocialListUiState
    data class ErrorLoadingMore(
        override val data: SocialListData,
        val requestedPage: Int,
        val error: SocialUiError,
    ) : SocialListUiState
}

class SocialListViewModel(
    private val username: String,
    private val type: SocialListType,
    private val repository: SocialRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SocialListUiState>(SocialListUiState.Loading())
    val uiState: StateFlow<SocialListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        loadPage(0, SocialListData())
    }

    fun loadMore() {
        val state = _uiState.value
        if (state !is SocialListUiState.Content || !state.data.hasNextPage || loadJob?.isActive == true) return
        loadPage(state.data.page + 1, state.data)
    }

    fun retry() = when (val state = _uiState.value) {
        is SocialListUiState.Error -> refresh()
        is SocialListUiState.ErrorLoadingMore -> loadPage(state.requestedPage, state.data)
        else -> Unit
    }

    private fun loadPage(page: Int, current: SocialListData) {
        _uiState.value = if (page == 0) SocialListUiState.Loading(current)
        else SocialListUiState.LoadingMore(current, page)
        loadJob = viewModelScope.launch {
            val result = when (type) {
                SocialListType.Followers -> repository.followers(username, page, PAGE_SIZE)
                SocialListType.Following -> repository.following(username, page, PAGE_SIZE)
            }
            _uiState.value = when (result) {
                is SocialResult.Failure -> if (page == 0) {
                    SocialListUiState.Error(current, result.error.toSocialUiError())
                } else {
                    SocialListUiState.ErrorLoadingMore(current, page, result.error.toSocialUiError())
                }
                is SocialResult.Success -> if (result.value.page != page) {
                    if (page == 0) SocialListUiState.Error(current, SocialUiError.InvalidResponse)
                    else SocialListUiState.ErrorLoadingMore(current, page, SocialUiError.InvalidResponse)
                } else {
                    val ids = current.users.mapTo(mutableSetOf(), PublicProfile::userId)
                    val updated = current.copy(
                        users = current.users + result.value.content.filter { ids.add(it.userId) },
                        page = page,
                        hasNextPage = !result.value.last,
                    )
                    if (updated.users.isEmpty()) SocialListUiState.Empty(updated)
                    else SocialListUiState.Content(updated)
                }
            }
        }
    }

    companion object { const val PAGE_SIZE = 20 }
}

class SocialListViewModelFactory(
    private val username: String,
    private val type: SocialListType,
    private val repository: SocialRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SocialListViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SocialListViewModel(username, type, repository) as T
    }
}

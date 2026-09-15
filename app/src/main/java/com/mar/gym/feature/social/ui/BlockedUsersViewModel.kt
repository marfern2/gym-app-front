package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialModerationRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.BlockedUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BlockedUsersData(
    val users: List<BlockedUser> = emptyList(),
    val page: Int = -1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: SocialUiError? = null,
    val unblockCandidate: BlockedUser? = null,
    val unblockingIds: Set<String> = emptySet(),
    val actionError: SocialUiError? = null,
)

sealed interface BlockedUsersUiState {
    data object Loading : BlockedUsersUiState
    data class Content(val data: BlockedUsersData) : BlockedUsersUiState
    data class Empty(val data: BlockedUsersData = BlockedUsersData()) : BlockedUsersUiState
    data class Error(val error: SocialUiError) : BlockedUsersUiState
}

class BlockedUsersViewModel(private val repository: SocialModerationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockedUsersUiState>(BlockedUsersUiState.Loading)
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        _uiState.value = BlockedUsersUiState.Loading
        loadPage(0, BlockedUsersData())
    }

    fun retry() {
        val data = _uiState.value.dataOrNull()
        if (data?.loadMoreError != null) loadPage(data.page + 1, data)
        else refresh()
    }

    fun loadMore() {
        val data = _uiState.value.dataOrNull() ?: return
        if (!data.hasMore || data.loadingMore || loadJob?.isActive == true) return
        loadPage(data.page + 1, data)
    }

    fun requestUnblock(user: BlockedUser) = updateData {
        if (user.userId in it.unblockingIds) it else it.copy(unblockCandidate = user, actionError = null)
    }

    fun cancelUnblock() = updateData { it.copy(unblockCandidate = null) }

    fun confirmUnblock() {
        val data = _uiState.value.dataOrNull() ?: return
        val user = data.unblockCandidate ?: return
        if (user.userId in data.unblockingIds) return
        setData(data.copy(
            unblockCandidate = null,
            unblockingIds = data.unblockingIds + user.userId,
            actionError = null,
        ))
        viewModelScope.launch {
            when (val result = repository.unblock(user.username)) {
                is SocialResult.Success -> updateData { latest ->
                    latest.copy(
                        users = latest.users.filterNot { it.userId == user.userId },
                        unblockingIds = latest.unblockingIds - user.userId,
                        actionError = null,
                    )
                }
                is SocialResult.Failure -> updateData { latest ->
                    latest.copy(
                        unblockingIds = latest.unblockingIds - user.userId,
                        actionError = result.error.toSocialUiError(),
                    )
                }
            }
        }
    }

    private fun loadPage(page: Int, current: BlockedUsersData) {
        if (page > 0) setData(current.copy(loadingMore = true, loadMoreError = null))
        loadJob = viewModelScope.launch {
            when (val result = repository.blockedUsers(page, PAGE_SIZE)) {
                is SocialResult.Failure -> if (page == 0) {
                    _uiState.value = BlockedUsersUiState.Error(result.error.toSocialUiError())
                } else setData(current.copy(
                    loadingMore = false,
                    loadMoreError = result.error.toSocialUiError(),
                ))
                is SocialResult.Success -> {
                    if (result.value.page != page) {
                        if (page == 0) _uiState.value = BlockedUsersUiState.Error(SocialUiError.InvalidResponse)
                        else setData(current.copy(loadingMore = false, loadMoreError = SocialUiError.InvalidResponse))
                    } else {
                        val ids = current.users.mapTo(mutableSetOf(), BlockedUser::userId)
                        setData(current.copy(
                            users = current.users + result.value.content.filter { ids.add(it.userId) },
                            page = page,
                            hasMore = !result.value.last,
                            loadingMore = false,
                            loadMoreError = null,
                        ))
                    }
                }
            }
        }
    }

    private inline fun updateData(transform: (BlockedUsersData) -> BlockedUsersData) {
        _uiState.value.dataOrNull()?.let { setData(transform(it)) }
    }

    private fun setData(data: BlockedUsersData) {
        _uiState.value = if (data.users.isEmpty()) BlockedUsersUiState.Empty(data) else BlockedUsersUiState.Content(data)
    }

    private fun BlockedUsersUiState.dataOrNull(): BlockedUsersData? = when (this) {
        is BlockedUsersUiState.Content -> data
        is BlockedUsersUiState.Empty -> data
        else -> null
    }

    companion object { const val PAGE_SIZE = 20 }
}

class BlockedUsersViewModelFactory(
    private val repository: SocialModerationRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(BlockedUsersViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return BlockedUsersViewModel(repository) as T
    }
}

package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialNotificationsRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialNotification
import com.mar.gym.feature.social.model.SocialNotificationType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SocialNotificationsUiState(
    val notifications: List<SocialNotification> = emptyList(),
    val page: Int = -1,
    val hasNextPage: Boolean = false,
    val initialLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: SocialUiError? = null,
    val loadMoreError: SocialUiError? = null,
    val unreadCount: Long = 0,
    val unreadCountLoaded: Boolean = false,
    val unreadCountError: SocialUiError? = null,
    val readingIds: Set<String> = emptySet(),
    val readAllInFlight: Boolean = false,
    val actionError: SocialUiError? = null,
)

sealed interface SocialNotificationDestination {
    data class Profile(val username: String) : SocialNotificationDestination
    data class Workout(val workoutId: String) : SocialNotificationDestination
    data class Comments(val workoutId: String) : SocialNotificationDestination
}

class SocialNotificationsViewModel(
    private val repository: SocialNotificationsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SocialNotificationsUiState())
    val uiState: StateFlow<SocialNotificationsUiState> = _uiState.asStateFlow()

    private var listJob: Job? = null
    private var countJob: Job? = null
    private var readAllVersion = 0L
    private val locallyReadIds = mutableSetOf<String>()

    init {
        refreshUnreadCount()
    }

    fun onHomeVisible() = refreshUnreadCount()

    fun openNotifications() {
        refresh()
        refreshUnreadCount()
    }

    fun refresh() {
        listJob?.cancel()
        _uiState.value = _uiState.value.copy(
            notifications = emptyList(),
            page = -1,
            hasNextPage = false,
            initialLoading = true,
            loadingMore = false,
            loaded = false,
            error = null,
            loadMoreError = null,
            actionError = null,
        )
        loadPage(page = 0, append = false)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.loaded || state.initialLoading || state.loadingMore || !state.hasNextPage || listJob?.isActive == true) return
        loadPage(page = state.page + 1, append = true)
    }

    fun retry() {
        val state = _uiState.value
        when {
            state.error != null -> refresh()
            state.loadMoreError != null -> loadPage(state.page + 1, append = true)
        }
    }

    fun notificationTapped(notification: SocialNotification): SocialNotificationDestination? {
        if (!notification.read) markRead(notification.id)
        if (!notification.targetAvailable) return null
        return when (notification.type) {
            SocialNotificationType.Follow -> SocialNotificationDestination.Profile(notification.actor.username)
            SocialNotificationType.WorkoutLike -> notification.workoutId?.let(SocialNotificationDestination::Workout)
            SocialNotificationType.WorkoutComment -> notification.workoutId?.let(SocialNotificationDestination::Comments)
        }
    }

    fun markAllRead() {
        val before = _uiState.value
        val hasUnread = before.unreadCount > 0L || before.notifications.any { !it.read }
        if (before.readAllInFlight || before.readingIds.isNotEmpty() || !hasUnread) return
        val unreadVisibleIds = before.notifications.filterNot(SocialNotification::read).mapTo(mutableSetOf(), SocialNotification::id)
        val previousCount = before.unreadCount
        locallyReadIds += unreadVisibleIds
        readAllVersion += 1
        val operationVersion = readAllVersion
        _uiState.value = before.copy(
            notifications = before.notifications.map { it.copy(read = true) },
            unreadCount = 0,
            readAllInFlight = true,
            actionError = null,
        )
        viewModelScope.launch {
            when (val result = repository.markAllRead()) {
                is SocialResult.Success -> {
                    if (operationVersion == readAllVersion) {
                        _uiState.value = _uiState.value.copy(readAllInFlight = false)
                    }
                    refreshUnreadCount(force = true)
                }
                is SocialResult.Failure -> {
                    if (operationVersion == readAllVersion) {
                        locallyReadIds -= unreadVisibleIds
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            notifications = current.notifications.map { item ->
                                if (item.id in unreadVisibleIds) item.copy(read = false) else item
                            },
                            unreadCount = previousCount,
                            readAllInFlight = false,
                            actionError = result.error.toSocialUiError(),
                        )
                    }
                    refreshUnreadCount(force = true)
                }
            }
        }
    }

    fun dismissActionError() {
        _uiState.value = _uiState.value.copy(actionError = null)
    }

    private fun markRead(notificationId: String) {
        val before = _uiState.value
        val item = before.notifications.firstOrNull { it.id == notificationId } ?: return
        if (item.read || notificationId in before.readingIds) return
        val operationReadAllVersion = readAllVersion
        locallyReadIds += notificationId
        _uiState.value = before.copy(
            notifications = before.notifications.map { if (it.id == notificationId) it.copy(read = true) else it },
            unreadCount = (before.unreadCount - 1).coerceAtLeast(0),
            readingIds = before.readingIds + notificationId,
            actionError = null,
        )
        viewModelScope.launch {
            when (val result = repository.markRead(notificationId)) {
                is SocialResult.Success -> {
                    _uiState.value = _uiState.value.copy(readingIds = _uiState.value.readingIds - notificationId)
                    refreshUnreadCount(force = true)
                }
                is SocialResult.Failure -> {
                    val current = _uiState.value
                    val shouldRollback = operationReadAllVersion == readAllVersion && !current.readAllInFlight
                    if (shouldRollback) locallyReadIds -= notificationId
                    _uiState.value = current.copy(
                        notifications = if (shouldRollback) current.notifications.map {
                            if (it.id == notificationId) it.copy(read = false) else it
                        } else current.notifications,
                        unreadCount = if (shouldRollback) current.unreadCount + 1 else current.unreadCount,
                        readingIds = current.readingIds - notificationId,
                        actionError = result.error.toSocialUiError(),
                    )
                    refreshUnreadCount(force = true)
                }
            }
        }
    }

    private fun refreshUnreadCount(force: Boolean = false) {
        if (countJob?.isActive == true) {
            if (!force) return
            countJob?.cancel()
        }
        countJob = viewModelScope.launch {
            when (val result = repository.unreadCount()) {
                is SocialResult.Success -> _uiState.value = _uiState.value.copy(
                    unreadCount = result.value,
                    unreadCountLoaded = true,
                    unreadCountError = null,
                )
                is SocialResult.Failure -> _uiState.value = _uiState.value.copy(
                    unreadCountLoaded = true,
                    unreadCountError = result.error.toSocialUiError(),
                )
            }
        }
    }

    private fun loadPage(page: Int, append: Boolean) {
        _uiState.value = if (append) {
            _uiState.value.copy(loadingMore = true, loadMoreError = null)
        } else {
            _uiState.value.copy(initialLoading = true, error = null)
        }
        listJob = viewModelScope.launch {
            when (val result = repository.notifications(page, PAGE_SIZE)) {
                is SocialResult.Failure -> _uiState.value = if (append) {
                    _uiState.value.copy(loadingMore = false, loadMoreError = result.error.toSocialUiError())
                } else {
                    _uiState.value.copy(initialLoading = false, loaded = true, error = result.error.toSocialUiError())
                }
                is SocialResult.Success -> {
                    if (result.value.page != page) {
                        val invalid = SocialUiError.InvalidResponse
                        _uiState.value = if (append) {
                            _uiState.value.copy(loadingMore = false, loadMoreError = invalid)
                        } else {
                            _uiState.value.copy(initialLoading = false, loaded = true, error = invalid)
                        }
                    } else {
                        val current = _uiState.value
                        val knownIds = if (append) {
                            current.notifications.mapTo(mutableSetOf(), SocialNotification::id)
                        } else mutableSetOf()
                        _uiState.value = current.copy(
                            notifications = (if (append) current.notifications else emptyList()) +
                                result.value.content.filter { knownIds.add(it.id) }.map { item ->
                                    if (item.id in locallyReadIds || current.readAllInFlight) item.copy(read = true) else item
                                },
                            page = page,
                            hasNextPage = !result.value.last,
                            initialLoading = false,
                            loadingMore = false,
                            loaded = true,
                            error = null,
                            loadMoreError = null,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        locallyReadIds.clear()
        _uiState.value = SocialNotificationsUiState()
    }

    companion object { const val PAGE_SIZE = 20 }
}

class SocialNotificationsViewModelFactory(
    private val repository: SocialNotificationsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SocialNotificationsViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SocialNotificationsViewModel(repository) as T
    }
}

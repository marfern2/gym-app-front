package com.mar.gym.feature.social.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SocialEngagement(
    val likesCount: Long,
    val isLikedByMe: Boolean,
    val commentsCount: Long,
)

data class SocialEngagementUiState(
    val workouts: Map<String, SocialEngagement> = emptyMap(),
    val likesInFlight: Set<String> = emptySet(),
    val likeErrors: Map<String, SocialUiError> = emptyMap(),
)

data class SocialCommentsData(
    val workoutId: String,
    val comments: List<SocialComment>,
    val page: Int,
    val hasMore: Boolean,
    val totalElements: Long,
    val loadingMore: Boolean = false,
    val loadMoreError: SocialUiError? = null,
    val input: String = "",
    val inputError: CommentInputError? = null,
    val submitting: Boolean = false,
    val deleteCandidate: SocialComment? = null,
    val deletingCommentIds: Set<String> = emptySet(),
    val actionError: SocialUiError? = null,
) {
    val canSubmit: Boolean
        get() = input.trim().isNotEmpty() && input.length <= MAX_COMMENT_LENGTH && !submitting
}

enum class CommentInputError { Empty, TooLong }

sealed interface SocialCommentsUiState {
    data object Idle : SocialCommentsUiState
    data class Loading(val workoutId: String) : SocialCommentsUiState
    data class Empty(val data: SocialCommentsData) : SocialCommentsUiState
    data class Content(val data: SocialCommentsData) : SocialCommentsUiState
    data class Error(val workoutId: String, val error: SocialUiError) : SocialCommentsUiState
    data class Unavailable(val workoutId: String) : SocialCommentsUiState
}

class SocialEngagementViewModel(
    private val currentUserId: String,
    private val repository: SocialFeedRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SocialEngagementUiState())
    val uiState: StateFlow<SocialEngagementUiState> = _uiState.asStateFlow()

    private val _commentsState = MutableStateFlow<SocialCommentsUiState>(SocialCommentsUiState.Idle)
    val commentsState: StateFlow<SocialCommentsUiState> = _commentsState.asStateFlow()
    private var commentsGeneration = 0

    fun toggleLike(workoutId: String, fallback: SocialEngagement) {
        val state = _uiState.value
        if (workoutId in state.likesInFlight) return
        val previous = state.workouts[workoutId] ?: fallback
        val optimistic = previous.copy(
            likesCount = if (previous.isLikedByMe) {
                (previous.likesCount - 1).coerceAtLeast(0)
            } else {
                previous.likesCount + 1
            },
            isLikedByMe = !previous.isLikedByMe,
        )
        _uiState.value = state.copy(
            workouts = state.workouts + (workoutId to optimistic),
            likesInFlight = state.likesInFlight + workoutId,
            likeErrors = state.likeErrors - workoutId,
        )
        viewModelScope.launch {
            val result = if (previous.isLikedByMe) repository.unlike(workoutId) else repository.like(workoutId)
            val latest = _uiState.value
            _uiState.value = when (result) {
                is SocialResult.Success -> latest.copy(likesInFlight = latest.likesInFlight - workoutId)
                is SocialResult.Failure -> {
                    val latestEngagement = latest.workouts[workoutId] ?: optimistic
                    latest.copy(
                        workouts = latest.workouts + (
                            workoutId to latestEngagement.copy(
                                likesCount = previous.likesCount,
                                isLikedByMe = previous.isLikedByMe,
                            )
                        ),
                        likesInFlight = latest.likesInFlight - workoutId,
                        likeErrors = latest.likeErrors + (workoutId to result.error.toSocialUiError()),
                    )
                }
            }
        }
    }

    fun openComments(workoutId: String, fallback: SocialEngagement) {
        ensureEngagement(workoutId, fallback)
        loadComments(workoutId)
    }

    fun ensureCommentsOpened(workoutId: String) {
        if (_commentsState.value.workoutIdOrNull() == workoutId) return
        openComments(workoutId, _uiState.value.workouts[workoutId] ?: SocialEngagement(0, false, 0))
    }

    fun retryComments() {
        val workoutId = when (val current = _commentsState.value) {
            is SocialCommentsUiState.Error -> current.workoutId
            is SocialCommentsUiState.Unavailable -> current.workoutId
            is SocialCommentsUiState.Loading -> current.workoutId
            is SocialCommentsUiState.Empty -> current.data.workoutId
            is SocialCommentsUiState.Content -> current.data.workoutId
            SocialCommentsUiState.Idle -> return
        }
        loadComments(workoutId)
    }

    fun loadMoreComments() {
        val data = _commentsState.value.dataOrNull() ?: return
        if (data.loadingMore || !data.hasMore) return
        val nextPage = data.page + 1
        _commentsState.value = data.copy(loadingMore = true, loadMoreError = null).asUiState()
        viewModelScope.launch {
            when (val result = repository.comments(data.workoutId, nextPage, COMMENTS_PAGE_SIZE)) {
                is SocialResult.Failure -> {
                    val error = result.error.toSocialUiError()
                    if (error.isUnavailable()) {
                        if (_commentsState.value.workoutIdOrNull() == data.workoutId) {
                            _commentsState.value = SocialCommentsUiState.Unavailable(data.workoutId)
                        }
                    } else {
                        updateCommentsData(data.workoutId) { latest ->
                            latest.copy(loadingMore = false, loadMoreError = error)
                        }
                    }
                }
                is SocialResult.Success -> updateCommentsData(data.workoutId) { latest ->
                    val knownIds = latest.comments.asSequence().map(SocialComment::id).toHashSet()
                    latest.copy(
                        comments = latest.comments + result.value.content.filter { knownIds.add(it.id) },
                        page = result.value.page,
                        hasMore = !result.value.last,
                        totalElements = result.value.totalElements,
                        loadingMore = false,
                        loadMoreError = null,
                    )
                }
            }
        }
    }

    fun onCommentInputChanged(value: String) {
        updateCommentsData { data ->
            data.copy(
                input = value,
                inputError = if (value.length > MAX_COMMENT_LENGTH) CommentInputError.TooLong else null,
                actionError = null,
            )
        }
    }

    fun submitComment() {
        val data = _commentsState.value.dataOrNull() ?: return
        val normalized = data.input.trim()
        when {
            data.submitting -> return
            normalized.isEmpty() -> {
                updateCommentsData { it.copy(inputError = CommentInputError.Empty) }
                return
            }
            normalized.length > MAX_COMMENT_LENGTH -> {
                updateCommentsData { it.copy(inputError = CommentInputError.TooLong) }
                return
            }
        }
        _commentsState.value = data.copy(submitting = true, inputError = null, actionError = null).asUiState()
        viewModelScope.launch {
            when (val result = repository.createComment(data.workoutId, normalized)) {
                is SocialResult.Failure -> {
                    val error = result.error.toSocialUiError()
                    if (error.isUnavailable()) {
                        if (_commentsState.value.workoutIdOrNull() == data.workoutId) {
                            _commentsState.value = SocialCommentsUiState.Unavailable(data.workoutId)
                        }
                    } else {
                        updateCommentsData(data.workoutId) { latest ->
                            latest.copy(submitting = false, actionError = error)
                        }
                    }
                }
                is SocialResult.Success -> updateCommentsData(data.workoutId) { latest ->
                    val alreadyPresent = latest.comments.any { it.id == result.value.id }
                    if (!alreadyPresent) adjustCommentsCount(latest.workoutId, 1)
                    latest.copy(
                        comments = if (alreadyPresent) latest.comments else listOf(result.value) + latest.comments,
                        totalElements = if (alreadyPresent) latest.totalElements else latest.totalElements + 1,
                        input = "",
                        inputError = null,
                        submitting = false,
                        actionError = null,
                    )
                }
            }
        }
    }

    fun requestDelete(comment: SocialComment) {
        if (comment.author.userId != currentUserId) return
        updateCommentsData { data ->
            if (data.comments.none { it.id == comment.id }) data else data.copy(deleteCandidate = comment)
        }
    }

    fun cancelDelete() = updateCommentsData { it.copy(deleteCandidate = null) }

    fun confirmDelete() {
        val data = _commentsState.value.dataOrNull() ?: return
        val comment = data.deleteCandidate ?: return
        if (comment.author.userId != currentUserId || comment.id in data.deletingCommentIds) return
        _commentsState.value = data.copy(
            deleteCandidate = null,
            deletingCommentIds = data.deletingCommentIds + comment.id,
            actionError = null,
        ).asUiState()
        viewModelScope.launch {
            when (val result = repository.deleteComment(data.workoutId, comment.id)) {
                is SocialResult.Success -> updateCommentsData(data.workoutId) { latest ->
                    val existed = latest.comments.any { it.id == comment.id }
                    if (existed) adjustCommentsCount(latest.workoutId, -1)
                    latest.copy(
                        comments = latest.comments.filterNot { it.id == comment.id },
                        totalElements = if (existed) (latest.totalElements - 1).coerceAtLeast(0) else latest.totalElements,
                        deletingCommentIds = latest.deletingCommentIds - comment.id,
                    )
                }
                is SocialResult.Failure -> {
                    val error = result.error.toSocialUiError()
                    updateCommentsData(data.workoutId) { latest ->
                        latest.copy(
                            deletingCommentIds = latest.deletingCommentIds - comment.id,
                            actionError = error,
                        )
                    }
                    if (error.isUnavailable()) {
                        reconcileCommentsAfterDeleteFailure(data.workoutId, error)
                    }
                }
            }
        }
    }

    fun isOwnComment(comment: SocialComment): Boolean = comment.author.userId == currentUserId

    private fun loadComments(workoutId: String) {
        val generation = ++commentsGeneration
        _commentsState.value = SocialCommentsUiState.Loading(workoutId)
        viewModelScope.launch {
            val result = repository.comments(workoutId, 0, COMMENTS_PAGE_SIZE)
            if (generation != commentsGeneration) return@launch
            when (result) {
                is SocialResult.Failure -> {
                    val error = result.error.toSocialUiError()
                    _commentsState.value = if (error.isUnavailable()) {
                        SocialCommentsUiState.Unavailable(workoutId)
                    } else {
                        SocialCommentsUiState.Error(workoutId, error)
                    }
                }
                is SocialResult.Success -> {
                    setCommentsCount(workoutId, result.value.totalElements)
                    _commentsState.value = SocialCommentsData(
                        workoutId = workoutId,
                        comments = result.value.content.distinctBy(SocialComment::id),
                        page = result.value.page,
                        hasMore = !result.value.last,
                        totalElements = result.value.totalElements,
                    ).asUiState()
                }
            }
        }
    }

    private fun reconcileCommentsAfterDeleteFailure(workoutId: String, originalError: SocialUiError) {
        viewModelScope.launch {
            when (val result = repository.comments(workoutId, 0, COMMENTS_PAGE_SIZE)) {
                is SocialResult.Failure -> {
                    val error = result.error.toSocialUiError()
                    if (error.isUnavailable()) {
                        if (_commentsState.value.workoutIdOrNull() == workoutId) {
                            _commentsState.value = SocialCommentsUiState.Unavailable(workoutId)
                        }
                    }
                }
                is SocialResult.Success -> updateCommentsData(workoutId) { latest ->
                    setCommentsCount(workoutId, result.value.totalElements)
                    latest.copy(
                        comments = result.value.content.distinctBy(SocialComment::id),
                        page = result.value.page,
                        hasMore = !result.value.last,
                        totalElements = result.value.totalElements,
                        actionError = originalError,
                    )
                }
            }
        }
    }

    private fun ensureEngagement(workoutId: String, fallback: SocialEngagement) {
        val current = _uiState.value
        if (workoutId !in current.workouts) {
            _uiState.value = current.copy(workouts = current.workouts + (workoutId to fallback.normalized()))
        }
    }

    private fun adjustCommentsCount(workoutId: String, delta: Long) {
        val current = _uiState.value
        val engagement = current.workouts[workoutId] ?: SocialEngagement(0, false, 0)
        _uiState.value = current.copy(
            workouts = current.workouts + (
                workoutId to engagement.copy(commentsCount = (engagement.commentsCount + delta).coerceAtLeast(0))
            ),
        )
    }

    private fun setCommentsCount(workoutId: String, count: Long) {
        val current = _uiState.value
        val engagement = current.workouts[workoutId] ?: SocialEngagement(0, false, 0)
        _uiState.value = current.copy(
            workouts = current.workouts + (workoutId to engagement.copy(commentsCount = count.coerceAtLeast(0))),
        )
    }

    private inline fun updateCommentsData(
        expectedWorkoutId: String? = null,
        transform: (SocialCommentsData) -> SocialCommentsData,
    ) {
        val current = _commentsState.value.dataOrNull() ?: return
        if (expectedWorkoutId != null && current.workoutId != expectedWorkoutId) return
        _commentsState.value = transform(current).asUiState()
    }

    private fun SocialEngagement.normalized() = copy(
        likesCount = likesCount.coerceAtLeast(0),
        commentsCount = commentsCount.coerceAtLeast(0),
    )

    private fun SocialCommentsData.asUiState(): SocialCommentsUiState =
        if (comments.isEmpty()) SocialCommentsUiState.Empty(this) else SocialCommentsUiState.Content(this)

    private fun SocialCommentsUiState.dataOrNull(): SocialCommentsData? = when (this) {
        is SocialCommentsUiState.Content -> data
        is SocialCommentsUiState.Empty -> data
        else -> null
    }

    private fun SocialCommentsUiState.workoutIdOrNull(): String? = when (this) {
        is SocialCommentsUiState.Loading -> workoutId
        is SocialCommentsUiState.Empty -> data.workoutId
        is SocialCommentsUiState.Content -> data.workoutId
        is SocialCommentsUiState.Error -> workoutId
        is SocialCommentsUiState.Unavailable -> workoutId
        SocialCommentsUiState.Idle -> null
    }

    private fun SocialUiError.isUnavailable(): Boolean =
        this == SocialUiError.NotFound || this == SocialUiError.Forbidden

    private companion object {
        const val COMMENTS_PAGE_SIZE = 30
    }
}

const val MAX_COMMENT_LENGTH = 1_000

class SocialEngagementViewModelFactory(
    private val currentUserId: String,
    private val repository: SocialFeedRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SocialEngagementViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SocialEngagementViewModel(currentUserId, repository) as T
    }
}

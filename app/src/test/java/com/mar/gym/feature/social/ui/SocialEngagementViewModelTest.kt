package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.social.data.SocialFeedRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.SocialAuthor
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialCommentPage
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SuggestedAthletePage
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialEngagementViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `like is optimistic and protected from double request`() = runTest {
        val gate = CompletableDeferred<SocialResult<Unit>>()
        val repository = FakeRepository().apply { likeGate = gate }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)

        viewModel.toggleLike(WORKOUT_ID, SocialEngagement(4, false, 2))
        viewModel.toggleLike(WORKOUT_ID, SocialEngagement(4, false, 2))
        runCurrent()

        val optimistic = viewModel.uiState.value
        assertEquals(SocialEngagement(5, true, 2), optimistic.workouts[WORKOUT_ID])
        assertEquals(1, repository.likeCalls)
        assertTrue(WORKOUT_ID in optimistic.likesInFlight)

        gate.complete(SocialResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(WORKOUT_ID in viewModel.uiState.value.likesInFlight)
    }

    @Test fun `unlike never goes below zero and failure rolls back complete like state`() = runTest {
        val gate = CompletableDeferred<SocialResult<Unit>>()
        val repository = FakeRepository().apply { unlikeGate = gate }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)

        viewModel.toggleLike(WORKOUT_ID, SocialEngagement(0, true, 8))
        runCurrent()
        assertEquals(SocialEngagement(0, false, 8), viewModel.uiState.value.workouts[WORKOUT_ID])

        gate.complete(SocialResult.Failure(NetworkFailure.Network()))
        advanceUntilIdle()
        assertEquals(SocialEngagement(0, true, 8), viewModel.uiState.value.workouts[WORKOUT_ID])
        assertEquals(SocialUiError.Network, viewModel.uiState.value.likeErrors[WORKOUT_ID])
    }

    @Test fun `comments load empty paginate and unavailable states safely`() = runTest {
        val repository = FakeRepository().apply {
            commentPages[0] = page(emptyList(), page = 0, total = 0, last = true)
        }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)
        viewModel.openComments(WORKOUT_ID, SocialEngagement(1, false, 9))
        advanceUntilIdle()

        assertTrue(viewModel.commentsState.value is SocialCommentsUiState.Empty)
        assertEquals(0, viewModel.uiState.value.workouts.getValue(WORKOUT_ID).commentsCount)

        repository.commentPages[0] = page(listOf(comment(COMMENT_ID)), 0, 2, last = false)
        repository.commentPages[1] = page(listOf(comment(COMMENT_ID_2)), 1, 2, last = true)
        viewModel.retryComments()
        advanceUntilIdle()
        viewModel.loadMoreComments()
        advanceUntilIdle()

        val content = viewModel.commentsState.value as SocialCommentsUiState.Content
        assertEquals(listOf(COMMENT_ID, COMMENT_ID_2), content.data.comments.map { it.id })
        assertFalse(content.data.hasMore)
        assertEquals(listOf(0, 0, 1), repository.commentPageCalls)

        repository.commentPages[0] = SocialResult.Failure(NetworkFailure.HttpUnknown(404, null))
        viewModel.retryComments()
        advanceUntilIdle()
        assertTrue(viewModel.commentsState.value is SocialCommentsUiState.Unavailable)
    }

    @Test fun `create validates input inserts once and increments comment count`() = runTest {
        val repository = FakeRepository().apply {
            commentPages[0] = page(emptyList(), 0, 0, last = true)
            createResult = SocialResult.Success(comment(COMMENT_ID, text = "Hello"))
        }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)
        viewModel.openComments(WORKOUT_ID, SocialEngagement(2, false, 0))
        advanceUntilIdle()

        viewModel.submitComment()
        assertEquals(0, repository.createCalls)
        assertEquals(CommentInputError.Empty, (viewModel.commentsState.value as SocialCommentsUiState.Empty).data.inputError)

        viewModel.onCommentInputChanged("x".repeat(MAX_COMMENT_LENGTH + 1))
        viewModel.submitComment()
        assertEquals(0, repository.createCalls)
        assertEquals(CommentInputError.TooLong, (viewModel.commentsState.value as SocialCommentsUiState.Empty).data.inputError)

        viewModel.onCommentInputChanged("  Hello  ")
        viewModel.submitComment()
        advanceUntilIdle()
        val content = viewModel.commentsState.value as SocialCommentsUiState.Content
        assertEquals(listOf(COMMENT_ID), content.data.comments.map { it.id })
        assertEquals("", content.data.input)
        assertEquals(1, viewModel.uiState.value.workouts.getValue(WORKOUT_ID).commentsCount)
        assertEquals(listOf("Hello"), repository.createdTexts)

        viewModel.onCommentInputChanged("Hello again")
        viewModel.submitComment()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.workouts.getValue(WORKOUT_ID).commentsCount)
        assertEquals(1, (viewModel.commentsState.value as SocialCommentsUiState.Content).data.comments.size)
    }

    @Test fun `only own comment can be deleted and count decrements after success`() = runTest {
        val own = comment(COMMENT_ID, CURRENT_USER_ID)
        val foreign = comment(COMMENT_ID_2, OTHER_USER_ID)
        val repository = FakeRepository().apply {
            commentPages[0] = page(listOf(own, foreign), 0, 2, last = true)
        }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)
        viewModel.openComments(WORKOUT_ID, SocialEngagement(0, false, 2))
        advanceUntilIdle()

        viewModel.requestDelete(foreign)
        assertNull((viewModel.commentsState.value as SocialCommentsUiState.Content).data.deleteCandidate)
        viewModel.confirmDelete()
        assertEquals(0, repository.deleteCalls)

        viewModel.requestDelete(own)
        assertEquals(own, (viewModel.commentsState.value as SocialCommentsUiState.Content).data.deleteCandidate)
        viewModel.confirmDelete()
        advanceUntilIdle()

        val content = viewModel.commentsState.value as SocialCommentsUiState.Content
        assertEquals(listOf(COMMENT_ID_2), content.data.comments.map { it.id })
        assertEquals(1, viewModel.uiState.value.workouts.getValue(WORKOUT_ID).commentsCount)
        assertEquals(1, repository.deleteCalls)
    }

    @Test fun `missing comment delete reconciles list and count without crashing`() = runTest {
        val own = comment(COMMENT_ID, CURRENT_USER_ID)
        val repository = FakeRepository().apply {
            commentPages[0] = page(listOf(own), 0, 1, last = true)
        }
        val viewModel = SocialEngagementViewModel(CURRENT_USER_ID, repository)
        viewModel.openComments(WORKOUT_ID, SocialEngagement(0, false, 1))
        advanceUntilIdle()

        repository.deleteResult = SocialResult.Failure(NetworkFailure.HttpUnknown(404, null))
        repository.commentPages[0] = page(emptyList(), 0, 0, last = true)
        viewModel.requestDelete(own)
        viewModel.confirmDelete()
        advanceUntilIdle()

        val empty = viewModel.commentsState.value as SocialCommentsUiState.Empty
        assertTrue(empty.data.comments.isEmpty())
        assertEquals(SocialUiError.NotFound, empty.data.actionError)
        assertEquals(0, viewModel.uiState.value.workouts.getValue(WORKOUT_ID).commentsCount)
    }

    private class FakeRepository : SocialFeedRepository {
        var likeGate: CompletableDeferred<SocialResult<Unit>>? = null
        var unlikeGate: CompletableDeferred<SocialResult<Unit>>? = null
        var likeCalls = 0
        var unlikeCalls = 0
        var deleteCalls = 0
        var createCalls = 0
        var deleteResult: SocialResult<Unit> = SocialResult.Success(Unit)
        val commentPages = mutableMapOf<Int, SocialResult<SocialCommentPage>>()
        val commentPageCalls = mutableListOf<Int>()
        val createdTexts = mutableListOf<String>()
        var createResult: SocialResult<SocialComment> = SocialResult.Success(comment(COMMENT_ID))

        override suspend fun like(workoutId: String): SocialResult<Unit> {
            likeCalls++
            return likeGate?.await() ?: SocialResult.Success(Unit)
        }
        override suspend fun unlike(workoutId: String): SocialResult<Unit> {
            unlikeCalls++
            return unlikeGate?.await() ?: SocialResult.Success(Unit)
        }
        override suspend fun comments(workoutId: String, page: Int, size: Int): SocialResult<SocialCommentPage> {
            commentPageCalls += page
            return commentPages.getValue(page)
        }
        override suspend fun createComment(workoutId: String, text: String): SocialResult<SocialComment> {
            createCalls++
            createdTexts += text
            return createResult
        }
        override suspend fun deleteComment(workoutId: String, commentId: String): SocialResult<Unit> {
            deleteCalls++
            return deleteResult
        }
        override suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage> = error("Not used")
        override suspend fun suggestions(page: Int, size: Int): SocialResult<SuggestedAthletePage> = error("Not used")
        override suspend fun userWorkouts(username: String, cursor: String?, size: Int): SocialResult<SocialWorkoutPage> =
            error("Not used")
        override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> = error("Not used")
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val CURRENT_USER_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        const val COMMENT_ID = "00000000-0000-4000-8000-000000000020"
        const val COMMENT_ID_2 = "00000000-0000-4000-8000-000000000021"

        fun comment(
            id: String,
            authorId: String = CURRENT_USER_ID,
            text: String = "Comment",
        ) = SocialComment(
            id,
            WORKOUT_ID,
            SocialAuthor(authorId, if (authorId == CURRENT_USER_ID) "me" else "other", "User", null),
            text,
            Instant.parse("2026-08-25T10:05:00Z"),
            null,
        )

        fun page(
            comments: List<SocialComment>,
            page: Int,
            total: Long,
            last: Boolean,
        ): SocialResult<SocialCommentPage> = SocialResult.Success(
            SocialCommentPage(comments, page, 30, total, if (last) page + 1 else page + 2, page == 0, last),
        )
    }
}

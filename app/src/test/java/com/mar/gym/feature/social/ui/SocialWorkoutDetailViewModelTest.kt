package com.mar.gym.feature.social.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialWorkoutDetailViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `detail loads by id and retry recovers from hidden workout`() = runTest {
        val repository = FakeFeedRepository().apply {
            result = SocialResult.Failure(
                NetworkFailure.HttpProblem(
                    404,
                    ProblemDetails(errorCode = "SOCIAL_CONTENT_NOT_FOUND"),
                    null,
                ),
            )
        }
        val viewModel = SocialWorkoutDetailViewModel(WORKOUT_ID, repository)
        advanceUntilIdle()
        assertEquals(SocialUiError.NotFound, (viewModel.uiState.value as SocialWorkoutDetailUiState.Error).error)

        repository.result = SocialResult.Success(detail())
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SocialWorkoutDetailUiState.Content)
        assertEquals(listOf(WORKOUT_ID, WORKOUT_ID), repository.ids)
    }

    private class FakeFeedRepository : SocialFeedRepository {
        var result: SocialResult<SocialWorkoutDetail> = SocialResult.Success(detail())
        val ids = mutableListOf<String>()
        override suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail> {
            ids += workoutId
            return result
        }
        override suspend fun feed(cursor: String?, size: Int) = SocialResult.Success(
            SocialWorkoutPage(emptyList(), null, false),
        )
        override suspend fun discover(cursor: String?, size: Int) = SocialResult.Success(
            SocialWorkoutPage(emptyList(), null, false),
        )
        override suspend fun suggestions(page: Int, size: Int) = SocialResult.Success(
            SuggestedAthletePage(emptyList(), 0, size, 0, 0, true, true),
        )
        override suspend fun userWorkouts(username: String, cursor: String?, size: Int) = SocialResult.Success(
            SocialWorkoutPage(emptyList(), null, false),
        )
        override suspend fun like(workoutId: String): SocialResult<Unit> = error("Not used")
        override suspend fun unlike(workoutId: String): SocialResult<Unit> = error("Not used")
        override suspend fun comments(workoutId: String, page: Int, size: Int): SocialResult<SocialCommentPage> =
            error("Not used")
        override suspend fun createComment(workoutId: String, text: String): SocialResult<SocialComment> =
            error("Not used")
        override suspend fun deleteComment(workoutId: String, commentId: String): SocialResult<Unit> =
            error("Not used")
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        fun detail() = SocialWorkoutDetail(
            WORKOUT_ID, "Workout", null,
            Instant.parse("2026-08-25T09:00:00Z"), Instant.parse("2026-08-25T10:00:00Z"), 3600,
            SocialAuthor("00000000-0000-4000-8000-000000000001", "alice", "Alice", null),
            emptyList(),
        )
    }
}

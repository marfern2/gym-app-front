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
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.workouts.model.WorkoutVisibility
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

    @Test fun `owner completed workout changes visibility and adopts new ETag`() = runTest {
        val workouts = FakeWorkoutRepository().apply {
            updateResult = WorkoutRepositoryResult.Success(document(1, WorkoutVisibility.Public))
        }
        val viewModel = SocialWorkoutDetailViewModel(
            WORKOUT_ID,
            FakeFeedRepository(),
            USER_ID,
            workouts,
        )
        advanceUntilIdle()
        val loaded = viewModel.uiState.value as SocialWorkoutDetailUiState.Content
        assertEquals(WorkoutVisibility.Private, loaded.workout.socialVisibility)
        assertTrue(loaded.ownerDocument != null)

        viewModel.updateVisibility(WorkoutVisibility.Public)
        advanceUntilIdle()

        val updated = viewModel.uiState.value as SocialWorkoutDetailUiState.Content
        assertEquals(WorkoutVisibility.Public, updated.workout.socialVisibility)
        assertEquals(1L, updated.ownerDocument?.etag?.version)
        assertEquals(0L, workouts.requests.single().second.version)
        assertEquals(1L, updated.visibilityChangeVersion)
    }

    @Test fun `foreign workout has no owner document or visibility mutation`() = runTest {
        val workouts = FakeWorkoutRepository()
        val viewModel = SocialWorkoutDetailViewModel(
            WORKOUT_ID,
            FakeFeedRepository(),
            OTHER_USER_ID,
            workouts,
        )
        advanceUntilIdle()

        val content = viewModel.uiState.value as SocialWorkoutDetailUiState.Content
        assertEquals(null, content.ownerDocument)
        viewModel.updateVisibility(WorkoutVisibility.Public)
        advanceUntilIdle()
        assertTrue(workouts.requests.isEmpty())
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

    private class FakeWorkoutRepository : WorkoutRepository {
        var getResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        var updateResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        val requests = mutableListOf<Pair<WorkoutVisibility, WorkoutEtag>>()
        override suspend fun getWorkout(workoutId: String) = getResult
        override suspend fun updateWorkoutVisibility(
            workoutId: String,
            visibility: WorkoutVisibility,
            etag: WorkoutEtag,
        ): WorkoutRepositoryResult<WorkoutDocument> {
            requests += visibility to etag
            return updateResult
        }
        override suspend fun getActiveWorkout(): WorkoutRepositoryResult<WorkoutDocument> = failure()
        override suspend fun startWorkout(routineId: String?): WorkoutRepositoryResult<WorkoutDocument> = failure()
        override suspend fun updateWorkout(
            workoutId: String,
            draft: WorkoutDraft,
            etag: WorkoutEtag,
        ): WorkoutRepositoryResult<WorkoutDocument> = failure()
        override suspend fun completeWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = failure()
        override suspend fun discardWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<Unit> = failure()
        override suspend fun getWorkoutHistory(page: Int, size: Int): WorkoutRepositoryResult<WorkoutHistoryPage> = failure()
        private fun <T> failure(): WorkoutRepositoryResult<T> = WorkoutRepositoryResult.Failure(NetworkFailure.Network())
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        fun detail() = SocialWorkoutDetail(
            WORKOUT_ID, "Workout", null,
            Instant.parse("2026-08-25T09:00:00Z"), Instant.parse("2026-08-25T10:00:00Z"), 3600,
            SocialAuthor(USER_ID, "alice", "Alice", null),
            emptyList(),
        )
        fun document(
            version: Long = 0,
            visibility: WorkoutVisibility = WorkoutVisibility.Private,
        ): WorkoutDocument {
            val started = Instant.parse("2026-08-25T09:00:00Z")
            return WorkoutDocument(
                WorkoutDetail(
                    WORKOUT_ID,
                    null,
                    null,
                    "Workout",
                    null,
                    WorkoutStatus.Completed,
                    started,
                    started.plusSeconds(3600),
                    3600,
                    started,
                    started.plusSeconds(3600),
                    version,
                    emptyList(),
                    visibility,
                ),
                WorkoutEtag.fromVersion(version)!!,
            )
        }
    }
}

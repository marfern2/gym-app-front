package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsDetailAndOrdersInstructions() = runTest {
        val repository = FakeExerciseTemplateRepository()
        val viewModel = ExerciseDetailViewModel(repository)

        viewModel.load(EXERCISE_ID)
        runCurrent()

        val state = viewModel.uiState.value as ExerciseDetailUiState.Content
        assertEquals(listOf(1, 2), state.detail.instructions.map { it.position })
        assertEquals(listOf(EXERCISE_ID), repository.detailRequests)
    }

    @Test
    fun mapsNotFoundSeparately() = runTest {
        val repository = FakeExerciseTemplateRepository(
            detailHandler = {
                ExerciseRepositoryResult.Failure(
                    NetworkFailure.HttpProblem(
                        statusCode = 404,
                        problem = ProblemDetails(errorCode = "EXERCISE_TEMPLATE_NOT_FOUND"),
                        correlationId = "not-found-id",
                    )
                )
            }
        )
        val viewModel = ExerciseDetailViewModel(repository)

        viewModel.load(EXERCISE_ID)
        runCurrent()

        assertEquals(
            ExerciseDetailUiState.NotFound("not-found-id"),
            viewModel.uiState.value,
        )
    }

    @Test
    fun exposesRecoverableErrorAndRetry() = runTest {
        var fails = true
        val repository = FakeExerciseTemplateRepository(
            detailHandler = {
                if (fails) networkFailure() else ExerciseRepositoryResult.Success(detail())
            }
        )
        val viewModel = ExerciseDetailViewModel(repository)
        viewModel.load(EXERCISE_ID)
        runCurrent()
        assertTrue(viewModel.uiState.value is ExerciseDetailUiState.Error)

        fails = false
        viewModel.retry()
        runCurrent()

        assertTrue(viewModel.uiState.value is ExerciseDetailUiState.Content)
        assertEquals(2, repository.detailRequests.size)
    }
}

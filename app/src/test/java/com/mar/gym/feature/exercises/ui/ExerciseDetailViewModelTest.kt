package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
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
        assertEquals(listOf(1, 2), state.document.detail.instructions.map { it.position })
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
                if (fails) networkFailure() else ExerciseRepositoryResult.Success(document())
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

    @Test
    fun globalDetailCannotTriggerModification() = runTest {
        val repository = FakeExerciseTemplateRepository()
        val viewModel = ExerciseDetailViewModel(repository)
        viewModel.load(EXERCISE_ID)
        runCurrent()

        viewModel.archive()
        viewModel.restore()
        runCurrent()

        assertTrue(repository.archiveRequests.isEmpty())
        assertTrue(repository.restoreRequests.isEmpty())
    }

    @Test
    fun customArchiveAndRestoreUseLatestCanonicalEtag() = runTest {
        val repository = FakeExerciseTemplateRepository(
            detailHandler = {
                ExerciseRepositoryResult.Success(
                    document(detail(source = ExerciseTemplateSource.Custom, version = 3))
                )
            },
        )
        val viewModel = ExerciseDetailViewModel(repository)
        viewModel.load(EXERCISE_ID)
        runCurrent()

        viewModel.archive()
        runCurrent()
        assertEquals(3L, repository.archiveRequests.single().second.version)
        val archived = (viewModel.uiState.value as ExerciseDetailUiState.Content).document
        assertTrue(archived.detail.archived)
        assertEquals(4L, archived.etag.version)

        viewModel.restore()
        runCurrent()
        assertEquals(4L, repository.restoreRequests.single().second.version)
        assertTrue(!(viewModel.uiState.value as ExerciseDetailUiState.Content).document.detail.archived)
    }

    @Test
    fun mutationConflictKeepsDocumentAndDoesNotRetry() = runTest {
        val original = document(detail(source = ExerciseTemplateSource.Custom, version = 5))
        val repository = FakeExerciseTemplateRepository(
            detailHandler = { ExerciseRepositoryResult.Success(original) },
            archiveHandler = { _, _ ->
                ExerciseRepositoryResult.Failure(
                    NetworkFailure.HttpProblem(
                        409,
                        ProblemDetails(errorCode = "EXERCISE_TEMPLATE_VERSION_CONFLICT"),
                        null,
                    )
                )
            },
        )
        val viewModel = ExerciseDetailViewModel(repository)
        viewModel.load(EXERCISE_ID)
        runCurrent()

        viewModel.archive()
        runCurrent()

        val state = viewModel.uiState.value as ExerciseDetailUiState.Conflict
        assertEquals(original.detail.id, state.document.detail.id)
        assertEquals(original.detail.version, state.document.detail.version)
        assertEquals(original.etag.version, state.document.etag.version)
        assertEquals(1, repository.archiveRequests.size)
    }
}

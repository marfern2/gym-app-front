package com.mar.gym.feature.workouts.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.system.MainDispatcherRule
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `active 404 becomes explicit no active and empty start publishes active`() = runTest {
        val repository = FakeWorkoutRepository().apply { activeResult = failure(404, "WORKOUT_NOT_FOUND") }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.NoActiveWorkout)

        repository.startResult = WorkoutRepositoryResult.Success(document())
        viewModel.startEmpty()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Active)
        assertEquals(listOf<String?>(null), repository.startedWith)
    }

    @Test
    fun `start from routine sends routine id`() = runTest {
        val repository = FakeWorkoutRepository().apply { activeResult = failure(404, "WORKOUT_NOT_FOUND") }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        repository.startResult = WorkoutRepositoryResult.Success(document())

        viewModel.startFromRoutine(ROUTINE_ID)
        advanceUntilIdle()

        assertEquals(listOf(ROUTINE_ID), repository.startedWith)
    }

    @Test
    fun `edit is local until save and canonical response replaces draft and etag`() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.updateTitle("Local title")
        assertEquals(0, repository.updateCalls)
        assertTrue(viewModel.uiState.value.data.hasUnsavedChanges)
        repository.updateResult = WorkoutRepositoryResult.Success(document(version = 1, title = "Canonical title"))
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals("Canonical title", viewModel.uiState.value.data.draft?.title)
        assertEquals(1L, viewModel.uiState.value.data.etag?.version)
        assertFalse(viewModel.uiState.value.data.hasUnsavedChanges)
    }

    @Test
    fun `version conflict preserves local draft and never retries`() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.updateTitle("Unsaved title")
        repository.updateResult = failure(409, "WORKOUT_VERSION_CONFLICT")

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Conflict)
        assertEquals("Unsaved title", viewModel.uiState.value.data.draft?.title)
        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun `complete validates saves dirty draft then uses refreshed etag`() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.updateTitle("Edited")
        repository.updateResult = WorkoutRepositoryResult.Success(document(version = 1, title = "Edited"))
        repository.completeResult = WorkoutRepositoryResult.Success(
            document(version = 2, title = "Edited", status = WorkoutStatus.Completed),
        )

        viewModel.complete()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals(1, repository.completeEtags.single().version)
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.NoActiveWorkout)
    }

    @Test
    fun `discard uses current etag once and clears active state`() = runTest {
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.discard()
        advanceUntilIdle()

        assertEquals(listOf(0L), repository.discardEtags.map { it.version })
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.NoActiveWorkout)
    }

    @Test
    fun `selected template becomes new exercise without server id`() = runTest {
        val viewModel = viewModel(FakeWorkoutRepository(), FakeExerciseRepository())
        advanceUntilIdle()

        viewModel.addSelectedExercises(linkedSetOf(TEMPLATE_ID))
        advanceUntilIdle()

        val exercise = viewModel.uiState.value.data.draft!!.exercises.single()
        assertEquals(TEMPLATE_ID, exercise.exerciseTemplateId)
        assertEquals(null, exercise.serverId)
    }

    @Test
    fun `workout accepts active custom and rejects archived custom`() = runTest {
        val active = viewModel(
            FakeWorkoutRepository(),
            FakeExerciseRepository(
                template = FakeExerciseRepository.exerciseDetail(
                    ExerciseTemplateSource.Custom,
                    archived = false,
                )
            ),
        )
        advanceUntilIdle()
        active.addSelectedExercises(setOf(TEMPLATE_ID))
        advanceUntilIdle()
        assertEquals(1, active.uiState.value.data.draft?.exercises?.size)

        val archived = viewModel(
            FakeWorkoutRepository(),
            FakeExerciseRepository(
                template = FakeExerciseRepository.exerciseDetail(
                    ExerciseTemplateSource.Custom,
                    archived = true,
                )
            ),
        )
        advanceUntilIdle()
        archived.addSelectedExercises(setOf(TEMPLATE_ID))
        advanceUntilIdle()
        assertTrue(archived.uiState.value is ActiveWorkoutUiState.Error)
        assertTrue(archived.uiState.value.data.draft?.exercises.orEmpty().isEmpty())
    }

    @Test
    fun `history exposes empty paging loading more and error loading more`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            historyResults += WorkoutRepositoryResult.Success(historyPage(emptyList(), 0, last = true))
        }
        val empty = WorkoutHistoryViewModel(repository)
        advanceUntilIdle()
        assertTrue(empty.uiState.value is WorkoutHistoryUiState.Empty)

        val pagingRepository = FakeWorkoutRepository().apply {
            historyResults += WorkoutRepositoryResult.Success(historyPage(listOf(historyItem("1")), 0, last = false))
            historyResults += WorkoutRepositoryResult.Failure(NetworkFailure.Network())
        }
        val paging = WorkoutHistoryViewModel(pagingRepository)
        advanceUntilIdle()
        assertTrue(paging.uiState.value is WorkoutHistoryUiState.Content)
        paging.loadMore()
        advanceUntilIdle()
        assertTrue(paging.uiState.value is WorkoutHistoryUiState.ErrorLoadingMore)
        assertEquals(1, paging.uiState.value.data.items.size)
    }

    private fun viewModel(
        repository: FakeWorkoutRepository,
        exercises: ExerciseTemplateRepository = FakeExerciseRepository(),
    ) = ActiveWorkoutViewModel(
        repository, exercises,
        Clock.fixed(Instant.parse("2026-08-08T10:20:00Z"), ZoneOffset.UTC),
    )

    private class FakeWorkoutRepository : WorkoutRepository {
        var activeResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        var startResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        var updateResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document(version = 1))
        var completeResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document(status = WorkoutStatus.Completed))
        var discardResult: WorkoutRepositoryResult<Unit> = WorkoutRepositoryResult.Success(Unit)
        val historyResults = ArrayDeque<WorkoutRepositoryResult<WorkoutHistoryPage>>()
        val startedWith = mutableListOf<String?>()
        val completeEtags = mutableListOf<WorkoutEtag>()
        val discardEtags = mutableListOf<WorkoutEtag>()
        var updateCalls = 0

        override suspend fun getActiveWorkout() = activeResult
        override suspend fun startWorkout(routineId: String?): WorkoutRepositoryResult<WorkoutDocument> {
            startedWith += routineId
            return startResult
        }
        override suspend fun getWorkout(workoutId: String) = activeResult
        override suspend fun updateWorkout(workoutId: String, draft: WorkoutDraft, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> {
            updateCalls++
            return updateResult
        }
        override suspend fun completeWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> {
            completeEtags += etag
            return completeResult
        }
        override suspend fun discardWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<Unit> {
            discardEtags += etag
            return discardResult
        }
        override suspend fun getWorkoutHistory(page: Int, size: Int) = historyResults.removeFirst()
    }

    private class FakeExerciseRepository(
        private val template: ExerciseTemplateDetail = exerciseDetail(),
    ) : ExerciseTemplateRepository {
        override suspend fun getExerciseTemplates(
            query: String?, filters: ExerciseFilters, page: Int, size: Int, sort: ExerciseSort,
        ): ExerciseRepositoryResult<ExerciseTemplatePage> = ExerciseRepositoryResult.Failure(NetworkFailure.Network())

        override suspend fun getExerciseTemplate(exerciseTemplateId: String) = ExerciseRepositoryResult.Success(
            ExerciseTemplateDocument(
                template.copy(id = exerciseTemplateId),
                ExerciseTemplateEtag.fromVersion(template.version)!!,
            ),
        )
        override suspend fun createCustomExercise(draft: CustomExerciseDraft) = networkExerciseFailure()
        override suspend fun replaceCustomExercise(draft: CustomExerciseDraft, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        override suspend fun archiveCustomExercise(exerciseTemplateId: String, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        override suspend fun restoreCustomExercise(exerciseTemplateId: String, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        private fun networkExerciseFailure(): ExerciseRepositoryResult<ExerciseTemplateDocument> =
            ExerciseRepositoryResult.Failure(NetworkFailure.Network())

        companion object {
            fun exerciseDetail(
                source: ExerciseTemplateSource = ExerciseTemplateSource.Global,
                archived: Boolean = false,
            ) = ExerciseTemplateDetail(
                TEMPLATE_ID, "press", "Press", null, MuscleGroup.Chest, emptyList(),
                Equipment.Barbell, ExerciseType.WeightReps, MovementPattern.HorizontalPush,
                emptyList(), source = source, archived = archived,
            )
        }
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000001"
        const val ROUTINE_ID = "00000000-0000-4000-8000-000000000002"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000003"

        fun document(
            version: Long = 0,
            title: String = "Workout",
            status: WorkoutStatus = WorkoutStatus.Active,
        ): WorkoutDocument {
            val started = Instant.parse("2026-08-08T10:00:00Z")
            val detail = WorkoutDetail(
                WORKOUT_ID, null, null, title, null, status, started,
                if (status == WorkoutStatus.Completed) started.plusSeconds(600) else null,
                600, started, started.plusSeconds(600), version, emptyList(),
            )
            return WorkoutDocument(detail, WorkoutEtag.fromVersion(version)!!)
        }

        fun failure(status: Int, code: String) = WorkoutRepositoryResult.Failure(
            NetworkFailure.HttpProblem(status, ProblemDetails(status = status, errorCode = code), null),
        )

        fun historyItem(suffix: String) = WorkoutHistoryItem(
            "00000000-0000-4000-8000-00000000000$suffix", "Workout", Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60), 60, 1, 1,
        )

        fun historyPage(items: List<WorkoutHistoryItem>, page: Int, last: Boolean) = WorkoutHistoryPage(
            items, page, 20, items.size.toLong(), if (last) page + 1 else page + 2, page == 0, last,
        )
    }
}

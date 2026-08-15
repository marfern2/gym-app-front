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
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import java.time.YearMonth
import com.mar.gym.feature.system.MainDispatcherRule
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    fun `superset edit preserves local grouping and stable ids on conflict`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(groupedDocument())
            updateResult = failure(409, "WORKOUT_VERSION_CONFLICT")
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val first = viewModel.uiState.value.data.draft!!.exercises.first()

        viewModel.dissolveSuperset(first.localId)
        viewModel.save()
        advanceUntilIdle()

        val retained = viewModel.uiState.value.data.draft!!.exercises
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Conflict)
        assertTrue(retained.all { it.supersetLocalId == null })
        assertEquals(listOf(FIRST_EXERCISE_ID, SECOND_EXERCISE_ID), retained.map { it.serverId })
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
            document(version = 2, title = "Canonical completed", status = WorkoutStatus.Completed),
        )

        viewModel.complete()
        advanceUntilIdle()

        assertEquals(1, repository.updateCalls)
        assertEquals(1, repository.completeEtags.single().version)
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Completed)
        assertEquals("Canonical completed", (viewModel.uiState.value as ActiveWorkoutUiState.Completed).summary.title)
    }

    @Test
    fun `complete error remains available for save retry without automatic retry`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            completeResult = WorkoutRepositoryResult.Failure(NetworkFailure.Network())
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.complete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Error)
        assertEquals(1, repository.completeEtags.size)
    }

    @Test
    fun `complete conflict preserves active draft and etag without retry`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            completeResult = failure(409, "WORKOUT_VERSION_CONFLICT")
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.complete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Conflict)
        assertEquals("Workout", viewModel.uiState.value.data.draft?.title)
        assertEquals(0L, viewModel.uiState.value.data.etag?.version)
        assertEquals(1, repository.completeEtags.size)
    }

    @Test
    fun `double complete tap sends one request and completed state clears only on exit`() = runTest {
        val gate = CompletableDeferred<WorkoutRepositoryResult<WorkoutDocument>>()
        val repository = FakeWorkoutRepository().apply { completeGate = gate }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.complete()
        viewModel.complete()
        runCurrent()

        assertEquals(1, repository.completeEtags.size)
        gate.complete(WorkoutRepositoryResult.Success(document(status = WorkoutStatus.Completed)))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Completed)

        viewModel.clearCompletedWorkout()
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
    fun `replacement reuses template lookup and preserves workout identity grouping and etag`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(groupedDocument())
        }
        val exercises = FakeExerciseRepository(
            template = FakeExerciseRepository.exerciseDetail().copy(
                name = "Sentadilla",
                equipment = Equipment.None,
                exerciseType = ExerciseType.BodyweightReps,
            ),
        )
        val viewModel = viewModel(repository, exercises)
        advanceUntilIdle()
        val before = viewModel.uiState.value.data.draft!!.exercises.first()
        val beforeEtag = viewModel.uiState.value.data.etag!!.version

        viewModel.replaceExercise(before.localId, THIRD_TEMPLATE_ID)
        advanceUntilIdle()

        val after = viewModel.uiState.value.data.draft!!.exercises.first()
        assertEquals(before.localId, after.localId)
        assertEquals(before.serverId, after.serverId)
        assertEquals(before.supersetLocalId, after.supersetLocalId)
        assertEquals(before.sets, after.sets)
        assertEquals(THIRD_TEMPLATE_ID, after.exerciseTemplateId)
        assertEquals("Sentadilla", after.exerciseNameSnapshot)
        assertEquals(ExerciseType.BodyweightReps, after.exerciseTypeSnapshot)
        assertEquals(beforeEtag, viewModel.uiState.value.data.etag!!.version)
        assertEquals(0, repository.updateCalls)
        assertTrue(viewModel.uiState.value.data.hasUnsavedChanges)
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

    private fun viewModel(
        repository: FakeWorkoutRepository,
        exercises: ExerciseTemplateRepository = FakeExerciseRepository(),
    ) = ActiveWorkoutViewModel(
        repository, exercises, FakeAnalyticsRepository(),
        Clock.fixed(Instant.parse("2026-08-08T10:20:00Z"), ZoneOffset.UTC),
    )

    private class FakeAnalyticsRepository : AnalyticsRepository {
        override suspend fun previousPerformance(exerciseTemplateIds: List<String>) = AnalyticsResult.Success(
            exerciseTemplateIds.map { PreviousPerformanceItem(it, null) }
        )
        override suspend fun calendar(month: YearMonth, timezone: String): AnalyticsResult<TrainingCalendar> = failure()
        override suspend fun summary(period: AnalyticsPeriod, timezone: String): AnalyticsResult<ProgressSummary> = failure()
        override suspend fun muscleDistribution(period: AnalyticsPeriod, timezone: String): AnalyticsResult<MuscleDistribution> = failure()
        override suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int): AnalyticsResult<ExerciseHistoryPage> = failure()
        override suspend fun personalRecords(exerciseTemplateId: String): AnalyticsResult<PersonalRecords> = failure()
        private fun <T> failure(): AnalyticsResult<T> = AnalyticsResult.Failure(NetworkFailure.Network())
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        var activeResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        var startResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document())
        var updateResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document(version = 1))
        var completeResult: WorkoutRepositoryResult<WorkoutDocument> = WorkoutRepositoryResult.Success(document(status = WorkoutStatus.Completed))
        var discardResult: WorkoutRepositoryResult<Unit> = WorkoutRepositoryResult.Success(Unit)
        var completeGate: CompletableDeferred<WorkoutRepositoryResult<WorkoutDocument>>? = null
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
            return completeGate?.await() ?: completeResult
        }
        override suspend fun discardWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<Unit> {
            discardEtags += etag
            return discardResult
        }
        override suspend fun getWorkoutHistory(page: Int, size: Int) =
            WorkoutRepositoryResult.Failure(NetworkFailure.Network())
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
        const val SECOND_TEMPLATE_ID = "00000000-0000-4000-8000-000000000004"
        const val THIRD_TEMPLATE_ID = "00000000-0000-4000-8000-000000000007"
        const val FIRST_EXERCISE_ID = "00000000-0000-4000-8000-000000000005"
        const val SECOND_EXERCISE_ID = "00000000-0000-4000-8000-000000000006"

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

        fun groupedDocument(): WorkoutDocument {
            val base = document()
            val exercises = listOf(
                com.mar.gym.feature.workouts.model.WorkoutExercise(
                    FIRST_EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps,
                    Equipment.Barbell, 1, null, 90, emptyList(), supersetGroup = 1,
                ),
                com.mar.gym.feature.workouts.model.WorkoutExercise(
                    SECOND_EXERCISE_ID, SECOND_TEMPLATE_ID, "Remo", ExerciseType.WeightReps,
                    Equipment.Barbell, 2, null, 90, emptyList(), supersetGroup = 1,
                ),
            )
            return base.copy(detail = base.detail.copy(exercises = exercises))
        }

        fun failure(status: Int, code: String) = WorkoutRepositoryResult.Failure(
            NetworkFailure.HttpProblem(status, ProblemDetails(status = status, errorCode = code), null),
        )

    }
}

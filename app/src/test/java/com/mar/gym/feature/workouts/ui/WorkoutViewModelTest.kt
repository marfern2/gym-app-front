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
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutSet
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.workouts.rest.RestTimer
import com.mar.gym.feature.workouts.rest.RestTimerController
import com.mar.gym.feature.workouts.rest.RestTimerNotifier
import com.mar.gym.feature.workouts.rest.RestTimerScheduler
import com.mar.gym.feature.workouts.rest.ScheduledRestTimerTask
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.math.BigDecimal
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
    fun `replacement clears hidden incompatible actuals and uncompletes invalid set`() = runTest {
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(setDocument())
        }
        val exercises = FakeExerciseRepository(
            template = FakeExerciseRepository.exerciseDetail().copy(
                exerciseType = ExerciseType.Duration,
            ),
        )
        val viewModel = viewModel(repository, exercises)
        advanceUntilIdle()
        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) {
            it.copy(
                setType = SetType.Drop,
                reps = "8",
                weight = "80",
                rpe = "8.5",
                completed = true,
            )
        }

        viewModel.replaceExercise(FIRST_EXERCISE_ID, THIRD_TEMPLATE_ID)
        advanceUntilIdle()

        val set = viewModel.uiState.value.data.draft!!.exercises.first().sets.single()
        assertEquals("", set.reps)
        assertEquals("", set.weight)
        assertEquals("8.5", set.rpe)
        assertEquals(SetType.Drop, set.setType)
        assertFalse(set.completed)
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
    fun `user incomplete to completed starts rest with current exercise and set origin`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply { activeResult = WorkoutRepositoryResult.Success(setDocument()) }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()

        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }

        val timer = rest.active.value!!
        assertEquals(WORKOUT_ID, timer.workoutId)
        assertEquals(FIRST_EXERCISE_ID, timer.exerciseLocalId)
        assertEquals(FIRST_SET_ID, timer.setLocalId)
        assertEquals("Press", timer.exerciseName)
        assertEquals(90, timer.configuredDurationSeconds)
        assertEquals("Press", timer.completedSet?.exerciseName)
        assertEquals(1, timer.completedSet?.setNumber)
        assertEquals("Remo", timer.upcomingSet?.exerciseName)
        assertEquals(1, timer.upcomingSet?.setNumber)
        assertEquals(1, timer.upcomingSet?.totalSets)
        assertEquals("70 kg x 7 reps", timer.upcomingSet?.metricSummary)
    }

    @Test
    fun `rest notification metrics adapt to exercise type and target fallback`() {
        val targets = WorkoutSetTargets(
            targetRepsMin = 8,
            targetRepsMax = 10,
            targetWeight = BigDecimal("60.000"),
            targetDurationSeconds = 75,
            targetDistanceMeters = BigDecimal("500.0"),
            targetRpe = null,
        )
        val set = com.mar.gym.feature.workouts.model.WorkoutSetDraft(
            localId = "set",
            serverId = null,
            targets = targets,
        )

        assertEquals("60 kg x 8–10 reps", set.restTimerMetricSummary(ExerciseType.WeightReps))
        assertEquals("8–10 reps", set.restTimerMetricSummary(ExerciseType.BodyweightReps))
        assertEquals("1:15", set.restTimerMetricSummary(ExerciseType.Duration))
        assertEquals("500 m / 1:15", set.restTimerMetricSummary(ExerciseType.DistanceDuration))
        assertEquals("60 kg / 500 m", set.restTimerMetricSummary(ExerciseType.WeightDistance))
    }

    @Test
    fun `zero rest completion starts nothing and replaces an existing rest with none`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(setDocument(secondRestSeconds = 0))
        }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }
        assertTrue(rest.active.value != null)

        viewModel.updateSet(SECOND_EXERCISE_ID, SECOND_SET_ID) { it.copy(completed = true) }

        assertEquals(null, rest.active.value)
    }

    @Test
    fun `loaded completed data and completed to incomplete never start rest`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(
                setDocument(firstCompleted = true, secondCompleted = true),
            )
        }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        assertEquals(null, rest.active.value)

        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = false) }

        assertEquals(null, rest.active.value)
    }

    @Test
    fun `completing another set replaces timer with that exercise rest`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply { activeResult = WorkoutRepositoryResult.Success(setDocument()) }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }

        viewModel.updateSet(SECOND_EXERCISE_ID, SECOND_SET_ID) { it.copy(completed = true) }

        assertEquals(SECOND_EXERCISE_ID, rest.active.value?.exerciseLocalId)
        assertEquals(SECOND_SET_ID, rest.active.value?.setLocalId)
        assertEquals(45, rest.active.value?.configuredDurationSeconds)
    }

    @Test
    fun `unchecking origin cancels but unchecking another set does not`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(setDocument(firstCompleted = true))
        }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        rest.replaceFromCompletedSet(WORKOUT_ID, FIRST_EXERCISE_ID, "Press", FIRST_SET_ID, 90)

        viewModel.updateSet(SECOND_EXERCISE_ID, SECOND_SET_ID) { it.copy(completed = false) }
        assertEquals(FIRST_SET_ID, rest.active.value?.setLocalId)

        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = false) }
        assertEquals(null, rest.active.value)
    }

    @Test
    fun `superset and set type do not alter rest trigger`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply {
            activeResult = WorkoutRepositoryResult.Success(setDocument(grouped = true, firstSetType = SetType.Drop))
        }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()

        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }

        assertEquals(90, rest.active.value?.configuredDurationSeconds)
        assertEquals(FIRST_SET_ID, rest.active.value?.setLocalId)
        assertEquals(SetType.Drop, viewModel.uiState.value.data.draft!!.exercises.first().sets.first().setType)
    }

    @Test
    fun `automatic rest trigger is independent from every exercise type`() = runTest {
        ExerciseType.entries.forEach { type ->
            val rest = restController()
            val repository = FakeWorkoutRepository().apply {
                activeResult = WorkoutRepositoryResult.Success(setDocument(firstExerciseType = type))
            }
            val viewModel = viewModel(repository, restTimerController = rest)
            advanceUntilIdle()

            viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }

            assertEquals(type.name, FIRST_SET_ID, rest.active.value?.setLocalId)
            assertEquals(type.name, 90, rest.active.value?.configuredDurationSeconds)
        }
    }

    @Test
    fun `completing and uncompleting preserves actuals and set type for every exercise type`() = runTest {
        val actualsByType = mapOf(
            ExerciseType.WeightReps to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop, reps = "8", weight = "80", rpe = "8.5",
            ),
            ExerciseType.BodyweightReps to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop, reps = "12", rpe = "8.5",
            ),
            ExerciseType.WeightedBodyweight to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop, reps = "6", weight = "20", rpe = "8.5",
            ),
            ExerciseType.AssistedBodyweight to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop, reps = "10", weight = "30", rpe = "8.5",
            ),
            ExerciseType.Duration to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop, durationSeconds = "90", rpe = "8.5",
            ),
            ExerciseType.DistanceDuration to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop,
                durationSeconds = "120", distanceMeters = "500", rpe = "8.5",
            ),
            ExerciseType.WeightDistance to WorkoutSetDraft(
                FIRST_SET_ID, FIRST_SET_ID, SetType.Drop,
                weight = "25", distanceMeters = "40", rpe = "8.5",
            ),
        )

        actualsByType.forEach { (type, actuals) ->
            val repository = FakeWorkoutRepository().apply {
                activeResult = WorkoutRepositoryResult.Success(
                    setDocument(firstExerciseType = type, firstSet = actuals),
                )
            }
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }
            viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = false) }

            val finalSet = viewModel.uiState.value.data.draft!!.exercises.first().sets.single()
            assertEquals(type.name, actuals, finalSet)
        }
    }

    @Test
    fun `automatic rest does not interact with manual workout clock`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository().apply { activeResult = WorkoutRepositoryResult.Success(setDocument()) }
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        viewModel.manualClockState.adjustTimerSeconds(30)
        viewModel.manualClockState.startTimer()
        val before = viewModel.manualClockState.snapshot()

        viewModel.updateSet(FIRST_EXERCISE_ID, FIRST_SET_ID) { it.copy(completed = true) }

        assertEquals(before, viewModel.manualClockState.snapshot())
        assertEquals(FIRST_SET_ID, rest.active.value?.setLocalId)
    }

    @Test
    fun `successful workout completion cleans active rest`() = runTest {
        val rest = restController()
        val repository = FakeWorkoutRepository()
        val viewModel = viewModel(repository, restTimerController = rest)
        advanceUntilIdle()
        rest.replaceFromCompletedSet(WORKOUT_ID, FIRST_EXERCISE_ID, "Press", FIRST_SET_ID, 90)

        viewModel.complete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ActiveWorkoutUiState.Completed)
        assertEquals(null, rest.active.value)
    }

    private fun viewModel(
        repository: FakeWorkoutRepository,
        exercises: ExerciseTemplateRepository = FakeExerciseRepository(),
        restTimerController: RestTimerController = restController(),
    ) = ActiveWorkoutViewModel(
        repository, exercises, FakeAnalyticsRepository(),
        Clock.fixed(Instant.parse("2026-08-08T10:20:00Z"), ZoneOffset.UTC),
        restTimerController,
    )

    private fun restController() = RestTimerController(
        Clock.fixed(Instant.parse("2026-08-08T10:20:00Z"), ZoneOffset.UTC),
        RestTimerScheduler { _, _ -> ScheduledRestTimerTask {} },
        object : RestTimerNotifier {
            override fun showActive(timer: RestTimer, remainingMillis: Long) = Unit
            override fun hideActive() = Unit
            override fun showFinished(timer: RestTimer) = Unit
        },
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
        const val FIRST_SET_ID = "00000000-0000-4000-8000-000000000008"
        const val SECOND_SET_ID = "00000000-0000-4000-8000-000000000009"

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

        fun setDocument(
            firstCompleted: Boolean = false,
            secondCompleted: Boolean = false,
            secondRestSeconds: Int = 45,
            grouped: Boolean = false,
            firstSetType: SetType = SetType.Normal,
            firstExerciseType: ExerciseType = ExerciseType.WeightReps,
            firstSet: WorkoutSetDraft? = null,
        ): WorkoutDocument {
            val base = document()
            val targets = WorkoutSetTargets(null, null, null, null, null, null)
            val canonicalFirstSet = firstSet?.let { set ->
                WorkoutSet(
                    id = FIRST_SET_ID,
                    position = 1,
                    setType = set.setType,
                    targets = set.targets,
                    completed = set.completed,
                    reps = set.reps.toIntOrNull(),
                    weight = set.weight.toBigDecimalOrNull(),
                    durationSeconds = set.durationSeconds.toIntOrNull(),
                    distanceMeters = set.distanceMeters.toBigDecimalOrNull(),
                    rpe = set.rpe.toBigDecimalOrNull(),
                )
            } ?: WorkoutSet(
                FIRST_SET_ID, 1, firstSetType, targets, firstCompleted,
                null, null, null, null, null,
            )
            val exercises = listOf(
                WorkoutExercise(
                    FIRST_EXERCISE_ID, TEMPLATE_ID, "Press", firstExerciseType,
                    Equipment.Barbell, 1, null, 90,
                    listOf(canonicalFirstSet),
                    supersetGroup = if (grouped) 1 else null,
                ),
                WorkoutExercise(
                    SECOND_EXERCISE_ID, SECOND_TEMPLATE_ID, "Remo", ExerciseType.WeightReps,
                    Equipment.Barbell, 2, null, secondRestSeconds,
                    listOf(
                        WorkoutSet(
                            SECOND_SET_ID, 1, SetType.Warmup, targets, secondCompleted,
                            7, BigDecimal("70"), null, null, null,
                        ),
                    ),
                    supersetGroup = if (grouped) 1 else null,
                ),
            )
            return base.copy(detail = base.detail.copy(exercises = exercises))
        }

        fun failure(status: Int, code: String) = WorkoutRepositoryResult.Failure(
            NetworkFailure.HttpProblem(status, ProblemDetails(status = status, errorCode = code), null),
        )

    }
}

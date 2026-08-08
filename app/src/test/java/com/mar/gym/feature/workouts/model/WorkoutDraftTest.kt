package com.mar.gym.feature.workouts.model

import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDraftTest {
    @Test
    fun `canonical mapping keeps targets separate and does not prefill results`() {
        val draft = WorkoutDraft.from(document(targetWeight = BigDecimal("80.000"), targetMin = 8, targetMax = 10))
        val set = draft.exercises.single().sets.single()

        assertEquals(BigDecimal("80.000"), set.targets.targetWeight)
        assertEquals(8, set.targets.targetRepsMin)
        assertEquals(10, set.targets.targetRepsMax)
        assertEquals("", set.weight)
        assertEquals("", set.reps)
        assertFalse(set.completed)
    }

    @Test
    fun `new manual set has no server id and no targets`() {
        val exercise = WorkoutDraft.from(document()).exercises.single().addSet { "local-new" }
        val added = exercise.sets.last()

        assertNull(added.serverId)
        assertEquals(WorkoutSetTargets(null, null, null, null, null, null), added.targets)
        assertEquals("", added.weight)
        assertEquals("", added.reps)
    }

    @Test
    fun `validation follows every exercise type and completed requirements`() {
        val validMetrics = mapOf(
            ExerciseType.WeightReps to WorkoutSetDraft("s", null, reps = "8", weight = "80", completed = true),
            ExerciseType.BodyweightReps to WorkoutSetDraft("s", null, reps = "8", completed = true),
            ExerciseType.WeightedBodyweight to WorkoutSetDraft("s", null, reps = "8", weight = "10", completed = true),
            ExerciseType.AssistedBodyweight to WorkoutSetDraft("s", null, reps = "8", weight = "20", completed = true),
            ExerciseType.Duration to WorkoutSetDraft("s", null, durationSeconds = "60", completed = true),
            ExerciseType.DistanceDuration to WorkoutSetDraft("s", null, durationSeconds = "60", distanceMeters = "500", completed = true),
            ExerciseType.WeightDistance to WorkoutSetDraft("s", null, weight = "20", distanceMeters = "50", completed = true),
        )
        validMetrics.forEach { (type, set) -> assertTrue(draft(type, set).validate().isValid) }

        assertFalse(draft(ExerciseType.Duration, WorkoutSetDraft("s", null, reps = "10")).validate().isValid)
        assertFalse(draft(ExerciseType.WeightReps, WorkoutSetDraft("s", null, completed = true)).validate().isValid)
        assertTrue(draft(ExerciseType.WeightReps, WorkoutSetDraft("s", null, reps = "0", weight = "0", completed = true)).validate().isValid)
    }

    @Test
    fun `completion toggle does not copy target values`() {
        val original = WorkoutDraft.from(document(targetWeight = BigDecimal("80"), targetMin = 8, targetMax = 10))
        val toggled = original.exercises.single().sets.single().copy(completed = true)

        assertEquals("", toggled.weight)
        assertEquals("", toggled.reps)
        assertFalse(original.copy(exercises = listOf(original.exercises.single().copy(sets = listOf(toggled)))).validate().isValid)
    }

    @Test
    fun `elapsed is derived from startedAt and injected clock`() {
        val started = Instant.parse("2026-08-08T10:00:00Z")
        val clock = Clock.fixed(Instant.parse("2026-08-08T10:20:05Z"), ZoneOffset.UTC)
        assertEquals(1_205L, elapsedWorkoutSeconds(started, clock))
    }

    private fun draft(type: ExerciseType, set: WorkoutSetDraft) = WorkoutDraft(
        workoutId = WORKOUT_ID,
        title = "Workout",
        exercises = listOf(WorkoutExerciseDraft(
            localId = "e", serverId = null, exerciseTemplateId = TEMPLATE_ID,
            exerciseNameSnapshot = "Exercise", exerciseTypeSnapshot = type,
            equipmentSnapshot = Equipment.None, sets = listOf(set),
        )),
    )

    private fun document(
        targetWeight: BigDecimal? = null,
        targetMin: Int? = null,
        targetMax: Int? = null,
    ): WorkoutDocument {
        val now = Instant.parse("2026-08-08T10:00:00Z")
        return WorkoutDocument(
            WorkoutDetail(
                WORKOUT_ID, null, null, "Workout", null, WorkoutStatus.Active,
                now, null, 0, now, now, 0,
                listOf(WorkoutExercise(
                    EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
                    1, null, 90, listOf(WorkoutSet(
                        SET_ID, 1, SetType.Normal,
                        WorkoutSetTargets(targetMin, targetMax, targetWeight, null, null, null),
                        false, null, null, null, null, null,
                    )),
                )),
            ),
            WorkoutEtag.fromVersion(0)!!,
        )
    }

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000001"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000002"
        const val SET_ID = "00000000-0000-4000-8000-000000000003"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000004"
    }
}
